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

package google.registry.mosapi.services;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.mosapi.client.ServiceMonitoringClient;
import google.registry.mosapi.dto.servicemonitoring.ActiveIncidentsSummary;
import google.registry.mosapi.dto.servicemonitoring.AllServicesStateResponse;
import google.registry.mosapi.dto.servicemonitoring.ServiceStateSummary;
import google.registry.mosapi.dto.servicemonitoring.ServiceStatus;
import google.registry.mosapi.dto.servicemonitoring.TldServiceState;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.metrics.MosApiMetrics;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** A service that provides business logic for interacting with MoSAPI Service State. */
public class MosApiStateService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ServiceMonitoringClient serviceMonitoringClient;
  private final List<String> tlds;

  private final String downStatus = "Down";
  private final ExecutorService tldExecutor;
  private final MosApiMetrics mosApiMetrics;

  @Inject
  public MosApiStateService(
      ServiceMonitoringClient serviceMonitoringClient,
      MosApiMetrics mosApiMetrics,
      @Config("mosapiTlds") List<String> tlds,
      @Named("mosapiTldExecutor") ExecutorService tldExecutor) {
    this.serviceMonitoringClient = serviceMonitoringClient;
    this.mosApiMetrics = mosApiMetrics;
    this.tlds = tlds;
    this.tldExecutor = tldExecutor;
  }

  /** Fetches and transforms the service state for a given TLD into a summary. */
  public ServiceStateSummary getServiceStateSummary(String tld) throws MosApiException {
    TldServiceState rawState = serviceMonitoringClient.getServiceState(tld);
    // Record metrics asynchronously ("Fire-and-Forget")
    // This call returns immediately because MosApiMetrics submits the work to its own executor.
    mosApiMetrics.recordState(rawState);
    return transformToSummary(rawState);
  }

  /** Fetches and transforms the service state for all configured TLDs. */
  public AllServicesStateResponse getAllServiceStateSummaries() {
    List<CompletableFuture<ServiceStateSummary>> futures =
        tlds.stream()
            .map(
                tld ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return getServiceStateSummary(tld);
                          } catch (MosApiException e) {
                            logger.atWarning().withCause(e).log(
                                "Failed to get service state for TLD %s.", tld);
                            return new ServiceStateSummary(tld, "ERROR", null);
                          }
                        },
                        tldExecutor))
            .collect(Collectors.toList());

    ImmutableList<ServiceStateSummary> summaries =
        futures.stream()
            .map(CompletableFuture::join) // Waits for all tasks to complete
            .collect(toImmutableList());

    return new AllServicesStateResponse(summaries);
  }

  private ServiceStateSummary transformToSummary(TldServiceState rawState) {
    List<ActiveIncidentsSummary> activeIncidents = null;
    if (downStatus.equalsIgnoreCase(rawState.getStatus())) {
      activeIncidents =
          rawState.getServiceStatuses().entrySet().stream()
              .filter(
                  entry -> {
                    ServiceStatus service = entry.getValue();
                    return service.getIncidents() != null && !service.getIncidents().isEmpty();
                  })
              .map(
                  entry ->
                      new ActiveIncidentsSummary(
                          // key is the service name
                          entry.getKey(),
                          entry.getValue().getEmergencyThreshold(),
                          entry.getValue().getIncidents()))
              .collect(Collectors.toList());
    }
    return new ServiceStateSummary(rawState.getTld(), rawState.getStatus(), activeIncidents);
  }
}
