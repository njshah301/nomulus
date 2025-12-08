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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricRegistryImpl;
import google.registry.mosapi.dto.servicemonitoring.ServiceStatus;
import google.registry.mosapi.dto.servicemonitoring.TldServiceState;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  // In-memory storage for the current state (The "Scoreboard")
  // Key = List of Label Values (e.g., ["com", "DNS"]), Value = The Metric Value
  private static final ConcurrentHashMap<List<String>, Long> tldStatusMap = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<List<String>, Long> serviceStatusMap = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<List<String>, Double> emergencyUsageMap = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<List<String>, Long> activeIncidentsMap = new ConcurrentHashMap<>();

  private static final ImmutableSet<LabelDescriptor> TLD_LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "The Top Level Domain"));

  private static final ImmutableSet<LabelDescriptor> SERVICE_LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "The Top Level Domain"),
          LabelDescriptor.create("service_type", "The service type (DNS, RDDS, etc.)"));

  // Register Metrics with a Callback that reads from the maps
  static {
    MetricRegistryImpl.getDefault()
        .newGauge(
            "/mosapi/tld_status",
            "TLD Status",
            "Overall health of the TLD. 1=Up, 0=Down, 1=UP-inconclusive",
            TLD_LABELS,
            () -> tldStatusMap.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(
                    entry -> ImmutableList.copyOf(entry.getKey()),
                    Map.Entry::getValue)), // <--- Pull data from our map
            Long.class);

    MetricRegistryImpl.getDefault()
        .newGauge(
            "/mosapi/service_status",
            "Service Status",
            "Status of specific service components. 1=Up, 0=Down, 2=Disabled, 3=Inconclusive",
            SERVICE_LABELS,
            () -> serviceStatusMap.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(
                    entry -> ImmutableList.copyOf(entry.getKey()),
                    Map.Entry::getValue)), // <--- Pull data from our map
            Long.class);

    MetricRegistryImpl.getDefault()
        .newGauge(
            "/mosapi/emergency_usage",
            "Emergency Usage",
            "Percentage of Emergency Threshold consumed",
            SERVICE_LABELS,
            () -> emergencyUsageMap.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(
                    entry -> ImmutableList.copyOf(entry.getKey()),
                    Map.Entry::getValue)), // <--- Pull data from our map
            Double.class);

    MetricRegistryImpl.getDefault()
        .newGauge(
            "/mosapi/active_incidents",
            "Active Incidents",
            "Count of open incidents (State=Active)",
            SERVICE_LABELS,
            () -> activeIncidentsMap.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(
                    entry -> ImmutableList.copyOf(entry.getKey()),
                    Map.Entry::getValue)), // <--- Pull data from our map
            Long.class);
  }

  @Inject
  public MosApiMetrics() {
    // No dependencies needed anymore!
  }

  /** Updates the in-memory state for the given TLD. */
  public void recordState(TldServiceState state) {
    if (state == null) {
      return;
    }

    // This logic runs fast enough to execute on the main thread.
    // If optimization is needed, only the parsing logic needs to be async,
    // but map updates are thread-safe and instant.
    updateMetrics(state);
  }

  private void updateMetrics(TldServiceState state) {
    try {
      String tld = state.getTld();

      // 1. Update TLD Status
      long tldStatusVal = parseTldStatus(state.getStatus());
      tldStatusMap.put(ImmutableList.of(tld), tldStatusVal);

      // 2. Update Service Health Metrics
      Map<String, ServiceStatus> services = state.getServiceStatuses();
      if (services != null) {
        for (Map.Entry<String, ServiceStatus> entry : services.entrySet()) {
          String serviceType = entry.getKey();
          ServiceStatus statusObj = entry.getValue();
          ImmutableList<String> labels = ImmutableList.of(tld, serviceType);

          // Update Service Status
          long serviceStatusVal = parseServiceStatus(statusObj.getStatus());
          serviceStatusMap.put(labels, serviceStatusVal);

          // Update Emergency Usage
          Double emergencyThreshold = statusObj.getEmergencyThreshold();
          if (emergencyThreshold != null) {
            emergencyUsageMap.put(labels, emergencyThreshold);
          }

          // Update Active Incidents
          long activeCount = 0;
          if (statusObj.getIncidents() != null) {
            activeCount =
                statusObj.getIncidents().stream()
                    .filter(i -> "Active".equalsIgnoreCase(i.getState()))
                    .count();
          }
          activeIncidentsMap.put(labels, activeCount);
        }
      }
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("Failed to update memory metrics for TLD %s", state.getTld());
    }
  }

  private long parseTldStatus(String status) {
    if (status == null) {
      return 1;
    }
    switch (Ascii.toUpperCase(status)) {
      case "UP":
      case "UP-INCONCLUSIVE":
        return 1;
      case "DOWN":
        return 0;
      default:
        return 1;
    }
  }

  private long parseServiceStatus(String status) {
    if (status == null) {
      return 1;
    }
    switch (Ascii.toUpperCase(status)) {
      case "UP":
        return 1;
      case "DOWN":
        return 0;
      case "DISABLED":
        return 2;
      case "INCONCLUSIVE":
        return 3;
      default:
        return 1;
    }
  }
}
