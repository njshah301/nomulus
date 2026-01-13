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
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.mosapi.MosApiModels.ServiceStatus;
import google.registry.mosapi.MosApiModels.TldServiceState;
import jakarta.inject.Inject;
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

  // Google Cloud Monitoring Limit: Max 200 TimeSeries per request
  private static final int MAX_TIMESERIES_PER_REQUEST = 195;

  // Magic String Constants
  private static final String METRIC_DOMAIN = "custom.googleapis.com/mosapi/";
  private static final String PROJECT_RESOURCE_PREFIX = "projects/";
  private static final String RESOURCE_TYPE_GLOBAL = "global";
  private static final String LABEL_PROJECT_ID = "project_id";
  private static final String LABEL_TLD = "tld";
  private static final String LABEL_SERVICE_TYPE = "service_type";

  // Metric Names
  private static final String METRIC_TLD_STATUS = "tld_status";
  private static final String METRIC_SERVICE_STATUS = "service_status";
  private static final String METRIC_EMERGENCY_USAGE = "emergency_usage";

  // MoSAPI Status Constants
  private static final String STATUS_UP_INCONCLUSIVE = "UP-INCONCLUSIVE";
  private static final String STATUS_DOWN = "DOWN";
  private static final String STATUS_DISABLED = "DISABLED";

  private final Monitoring monitoringClient;
  private final String projectId;
  private final ExecutorService executor;

  @Inject
  public MosApiMetrics(
      Monitoring monitoringClient,
      @Config("projectId") String projectId,
      @Named("mosapiMetricsExecutor") ExecutorService executor) {
    this.monitoringClient = monitoringClient;
    this.projectId = projectId;
    this.executor = executor;
  }

  /** Accepts a list of states and processes them in a single async batch task. */
  public void recordStates(List<TldServiceState> states) {
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

    for (List<TimeSeries> chunk : Lists.partition(allTimeSeries, MAX_TIMESERIES_PER_REQUEST)) {
      CreateTimeSeriesRequest request = new CreateTimeSeriesRequest().setTimeSeries(chunk);
      monitoringClient
          .projects()
          .timeSeries()
          .create(PROJECT_RESOURCE_PREFIX + projectId, request)
          .execute();
      logger.atInfo().log(
          "Successfully pushed batch of %d time series to Cloud Monitoring.", chunk.size());
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

    list.add(
        createTimeSeries(
            METRIC_SERVICE_STATUS, labels, parseServiceStatus(statusObj.status()), interval));

    list.add(
        createTimeSeries(METRIC_EMERGENCY_USAGE, labels, statusObj.emergencyThreshold(), interval));
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

  /**
   * Translates MoSAPI status to a numeric metric.
   *
   * <p>Mappings: 1 (UP) = Healthy; 0 (DOWN) = Critical failure; 2 (UP-INCONCLUSIVE) = Disabled/Not
   * Monitored/In Maintenance.
   *
   * <p>A status of 2 indicates the SLA monitoring system is under maintenance. The TLD is
   * considered "UP" by default, but individual service checks are disabled. This distinguishes
   * maintenance windows from actual availability or outages.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Spec Sec 5.1</a>
   */
  private long parseTldStatus(String status) {
    if (status == null) {
      return 1;
    }
    return switch (Ascii.toUpperCase(status)) {
      case STATUS_DOWN -> 0;
      case STATUS_UP_INCONCLUSIVE -> 2;
      default -> 1; // status is up
    };
  }

  /**
   * Translates MoSAPI service status to a numeric metric.
   *
   * <p>Mappings: 1 (UP) = Healthy; 0 (DOWN) = Critical failure; 2 (DISABLED/UP-INCONCLUSIVE*) =
   * Disabled/Not Monitored/In Maintenance.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Spec Sec 5.1</a>
   */
  private long parseServiceStatus(String status) {
    if (status == null) {
      return 1;
    }
    String serviceStatus = Ascii.toUpperCase(status);
    if (serviceStatus.startsWith(STATUS_UP_INCONCLUSIVE)) {
      return 2;
    }
    return switch (serviceStatus) {
      case STATUS_DOWN -> 0;
      case STATUS_DISABLED -> 2;
      default -> 1; // status is Up
    };
  }
}
