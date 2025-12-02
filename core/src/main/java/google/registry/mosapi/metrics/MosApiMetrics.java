// Copyright 2025 The Nomulus Authors. All Rights Reserved.
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

package google.registry.mosapi.metrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import com.google.monitoring.metrics.MetricRegistryImpl;
import com.google.monitoring.metrics.MetricWriter;
import google.registry.mosapi.dto.servicemonitoring.ServiceStatus;
import google.registry.mosapi.dto.servicemonitoring.TldServiceState;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Metrics Exporter for MoSAPI.
 *
 * <p>Transforms JSON data from MoSAPI
 * into numeric gauge values and exports them to Cloud Monitoring.
 * Uses a "Fire-and-Forget" pattern to avoid impacting request latency.
 *
 * @see <a href="go/mosapi-design">MOSAPI Design Doc</a>
 */
public class MosApiMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<LabelDescriptor> TLD_LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "The Top Level Domain"));

  private static final ImmutableSet<LabelDescriptor> SERVICE_LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "The Top Level Domain"),
          LabelDescriptor.create("service_type", "The service type (DNS, RDDS, etc.)"));

  // Defined with empty callbacks so the default background reporter effectively ignores them.
  // We use these objects solely for their metadata when pushing manually.
  private static final Metric<Integer> mosapiTldStatus =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/mosapi/tld_status",
              "TLD Status",
              "Overall health of the TLD. 1=Up, 0=Down, 1=UP-inconclusive",
              TLD_LABELS,
              ImmutableMap::of, // Empty callback
              Integer.class
              );

  private static final Metric<Integer> mosapiServiceStatus =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/mosapi/service_status",
              "Service Status",
              "Status of specific service components. 1=Up, 0=Down, 2=Disabled, 3=Inconclusive",
              SERVICE_LABELS,
              ImmutableMap::of, // Empty callback
              Integer.class
              );

  private static final Metric<Double> mosapiEmergencyUsage =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/mosapi/emergency_usage",
              "Emergency Usage",
              "Percentage of Emergency Threshold consumed",
              SERVICE_LABELS,
              ImmutableMap::of, // Empty callback
              Double.class
              );

  private static final Metric<Long> mosapiActiveIncidents =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/mosapi/active_incidents",
              "Active Incidents",
              "Count of open incidents (State=Active)",
              SERVICE_LABELS,
              ImmutableMap::of, // Empty callback
              Long.class
              );

  private final ExecutorService asyncExecutor;
  private final MetricWriter metricWriter;

  @Inject
  public MosApiMetrics(
      @Named("mosapiMetricsExecutor") ExecutorService asyncExecutor,
      MetricWriter metricWriter) {
    this.asyncExecutor = asyncExecutor;
    this.metricWriter = metricWriter;

  }

  /**
   * Asynchronously pushes metrics for the given TLD service state.
   */
  public void recordState(TldServiceState state) {
    if (state == null) {
      return;
    }
    asyncExecutor.execute(() -> pushMetrics(state));
  }

  private void pushMetrics(TldServiceState state) {
    try {
      String tld = state.getTld();
      Instant now = Instant.now();
      List<MetricPoint<?>> points = new ArrayList<>();

      // 1. TLD Status
      int tldStatusVal = parseTldStatus(state.getStatus());
      points.add(
          MetricPoint.create(
              mosapiTldStatus,
              ImmutableList.of(tld),
              now,
              tldStatusVal));

      // 2. Service Health Metrics
      Map<String, ServiceStatus> services = state.getServiceStatuses();
      if (services != null) {
        for (Map.Entry<String, ServiceStatus> entry : services.entrySet()) {
          String serviceType = entry.getKey();
          ServiceStatus statusObj = entry.getValue();
          ImmutableList<String> serviceLabelValues = ImmutableList.of(tld, serviceType);

          // Service Status
          points.add(
              MetricPoint.create(
                  mosapiServiceStatus,
                  serviceLabelValues,
                  now,
                  parseServiceStatus(statusObj.getStatus())));

          // Emergency Usage
          points.add(
              MetricPoint.create(
                  mosapiEmergencyUsage,
                  serviceLabelValues,
                  now,
                  statusObj.getEmergencyThreshold()));

          // Active Incidents
          long activeCount = 0;
          if (statusObj.getIncidents() != null) {
            activeCount = statusObj.getIncidents().stream()
                .filter(i -> "Active".equalsIgnoreCase(i.getState()))
                .count();
          }
          points.add(
              MetricPoint.create(
                  mosapiActiveIncidents,
                  serviceLabelValues,
                  now,
                  activeCount));
        }
      }

      // Push all points immediately
      // Push points one by one to satisfy MetricWriter signature
      for (MetricPoint<?> point : points) {
        writePoint(point);
      }
      logger.atInfo().log("Exported %d MoSAPI metrics for TLD %s", points.size(), tld);

    } catch (Exception e) {
      logger.atWarning().withCause(e).
          log("Failed to export MoSAPI metrics for TLD %s", state.getTld());
    }
  }
  // Helper method to handle wildcard capture for MetricPoint<?>
  private <V> void writePoint(MetricPoint<V> point) throws IOException {
    metricWriter.write(point);
  }
  private int parseTldStatus(String status) {
    if (status == null) return 0;
    switch (Ascii.toUpperCase(status)) {
      case "UP":
      case "UP-INCONCLUSIVE":
        return 1;
      case "DOWN":
        return 0;
      default:
        return 0;
    }
  }

  private int parseServiceStatus(String status) {
    if (status == null) return 0;
    switch (Ascii.toUpperCase(status)) {
      case "UP": return 1;
      case "DOWN": return 0;
      case "DISABLED": return 2;
      case "INCONCLUSIVE": return 3;
      default: return 0;
    }
  }
}
