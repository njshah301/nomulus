// Copyright 2026 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.mosapi;

import com.google.api.client.util.DateTime;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest;
import com.google.api.services.monitoring.v3.model.Metric;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.api.services.monitoring.v3.model.TypedValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.mosapi.MosApiModels.ServiceStatus;
import google.registry.mosapi.MosApiModels.TldServiceState;
import jakarta.inject.Inject;
import com.google.common.collect.Lists;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Metrics Exporter for MoSAPI. */
public class MosApiMetrics {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int MAX_TIMESERIES_PER_REQUEST = 200;

  // Magic String Constants
  private static final String METRIC_DOMAIN = "custom.googleapis.com/mosapi/";
  private static final String PROJECT_RESOURCE_PREFIX = "projects/";
  private static final String RESOURCE_TYPE_GLOBAL = "global";
  private static final String LABEL_PROJECT_ID = "project_id";
  private static final String LABEL_TLD = "tld";
  private static final String LABEL_SERVICE_TYPE = "service_type";
  private static final String LABEL_FALSE_POSITIVE = "is_false_positive";

  // Metric Names
  private static final String METRIC_TLD_STATUS = "tld_status";
  private static final String METRIC_SERVICE_STATUS = "service_status";
  private static final String METRIC_EMERGENCY_USAGE = "emergency_usage";
  private static final String METRIC_ACTIVE_INCIDENTS = "active_incidents";

  // MoSAPI Status Constants
  private static final String STATUS_UP = "UP";
  private static final String STATUS_UP_INCONCLUSIVE = "UP-INCONCLUSIVE";
  private static final String STATUS_DOWN = "DOWN";
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_DISABLED = "DISABLED";
  private static final String STATUS_INCONCLUSIVE = "INCONCLUSIVE";

  private final Monitoring monitoringClient;
  private final String projectId;
  private final ExecutorService executor;

  @Inject
  public MosApiMetrics(
      Monitoring monitoringClient,
      @Config("projectId") String projectId,
      @Named("mosapiTldExecutor") ExecutorService executor) {
    this.monitoringClient = monitoringClient;
    this.projectId = projectId;
    this.executor = executor;
  }

  /** Accepts a list of states and processes them in a single async batch task. */
  public void recordStates(List<TldServiceState> states) {
    if (states == null || states.isEmpty()) {
      return;
    }
    executor.execute(
        () -> {
          try {
            pushBatchMetrics(states);
          } catch (Throwable t) {
            logger.atWarning().withCause(t).log("Async batch metric push failed.");
          }
        });
  }

  private void pushBatchMetrics(List<TldServiceState> states) throws IOException {
    List<TimeSeries> allTimeSeries = new ArrayList<>();
    TimeInterval interval =
        new TimeInterval().setEndTime(new DateTime(System.currentTimeMillis()).toString());

    for (TldServiceState state : states) {
      // 1. TLD Status Metric
      allTimeSeries.add(createTldStatusTimeSeries(state, interval));

      // 2. Service-level Metrics
      Map<String, ServiceStatus> services = state.serviceStatuses();
      if (services != null) {
        for (Map.Entry<String, ServiceStatus> entry : services.entrySet()) {
          addServiceMetrics(allTimeSeries, state.tld(), entry.getKey(), entry.getValue(), interval);
        }
      }
    }

    // Google Cloud Monitoring Limit: Max 200 TimeSeries per request
    for (List<TimeSeries> chunk : Lists.partition(allTimeSeries, MAX_TIMESERIES_PER_REQUEST)) {
      CreateTimeSeriesRequest request = new CreateTimeSeriesRequest().setTimeSeries(chunk);
      monitoringClient
          .projects()
          .timeSeries()
          .create(PROJECT_RESOURCE_PREFIX + projectId, request)
          .execute();
      logger.atInfo().log("Successfully pushed batch of %d time series to Cloud Monitoring.", chunk.size());
    }
  }

  private void addServiceMetrics(
      List<TimeSeries> list,
      String tld,
      String serviceType,
      ServiceStatus statusObj,
      TimeInterval interval) {
    ImmutableMap<String, String> labels =
        ImmutableMap.of(LABEL_TLD, tld, LABEL_SERVICE_TYPE, serviceType);

    list.add(createTimeSeries(METRIC_SERVICE_STATUS, labels, parseServiceStatus(statusObj.status()), interval));


    list.add(createTimeSeries(METRIC_EMERGENCY_USAGE, labels, statusObj.emergencyThreshold(), interval));


    if (statusObj.incidents() != null) {
      addIncidentMetrics(list, labels, statusObj, interval);
    }
  }

  private void addIncidentMetrics(
      List<TimeSeries> list,
      Map<String, String> baseLabels,
      ServiceStatus statusObj,
      TimeInterval interval) {
    long truePositives =
        statusObj.incidents().stream()
            .filter(i -> STATUS_ACTIVE.equalsIgnoreCase(i.state()) && !i.falsePositive())
            .count();
    long falsePositives =
        statusObj.incidents().stream()
            .filter(i -> STATUS_ACTIVE.equalsIgnoreCase(i.state()) && i.falsePositive())
            .count();

    list.add(createTimeSeries(METRIC_ACTIVE_INCIDENTS,
        combineLabels(baseLabels, LABEL_FALSE_POSITIVE, "false"), truePositives, interval));
    list.add(createTimeSeries(METRIC_ACTIVE_INCIDENTS,
        combineLabels(baseLabels, LABEL_FALSE_POSITIVE, "true"), falsePositives, interval));
  }

  private TimeSeries createTldStatusTimeSeries(TldServiceState state, TimeInterval interval) {
    return createTimeSeries(
        METRIC_TLD_STATUS,
        ImmutableMap.of(LABEL_TLD, state.tld()),
        parseTldStatus(state.status()),
        interval);
  }

  private TimeSeries createTimeSeries(
      String suffix, Map<String, String> labels, Number val, TimeInterval interval) {
    Metric metric = new Metric().setType(METRIC_DOMAIN + suffix).setLabels(labels);
    MonitoredResource resource =
        new MonitoredResource()
            .setType(RESOURCE_TYPE_GLOBAL)
            .setLabels(Collections.singletonMap(LABEL_PROJECT_ID, projectId));

    TypedValue tv = new TypedValue();
    if (val instanceof Double) {
      tv.setDoubleValue((Double) val);
    } else {
      tv.setInt64Value(val.longValue());
    }

    return new TimeSeries()
        .setMetric(metric)
        .setResource(resource)
        .setPoints(ImmutableList.of(new Point().setInterval(interval).setValue(tv)));
  }

  private Map<String, String> combineLabels(Map<String, String> base, String key, String value) {
    return ImmutableMap.<String, String>builder().putAll(base).put(key, value).build();
  }

  private long parseTldStatus(String status) {
    if (status == null) return 1;
    String s = Ascii.toUpperCase(status);
    if (s.equals(STATUS_UP) || s.equals(STATUS_UP_INCONCLUSIVE)) return 1;
    if (s.equals(STATUS_DOWN)) return 0;
    return 1;
  }

  private long parseServiceStatus(String status) {
    if (status == null) return 1;
    String s = Ascii.toUpperCase(status);
    if (s.startsWith(STATUS_UP)) return 1;
    return switch (s) {
      case STATUS_DOWN -> 0;
      case STATUS_DISABLED -> 2;
      case STATUS_INCONCLUSIVE -> 3;
      default -> 1;
    };
  }
}