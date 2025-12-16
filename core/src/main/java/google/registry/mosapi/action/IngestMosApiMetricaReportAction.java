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

package google.registry.mosapi.action;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.mosapi.MosApiThreatMatch;
import google.registry.model.mosapi.MosApiThreatMatchDao;
import google.registry.mosapi.dto.domainmetrica.MetricaReport;
import google.registry.mosapi.dto.domainmetrica.MetricaReportInfo;
import google.registry.mosapi.dto.domainmetrica.MetricaThreatData;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.services.MosApiMetricaService;
import google.registry.request.Action;
import google.registry.request.auth.Auth;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Action to ingest daily Domain METRICA reports from MoSAPI.
 *
 * <p>This action fetches reports for all configured MoSAPI TLDs. It employs a "catch-up" strategy:
 * querying the database for the last processed date and fetching all subsequent reports up to the
 * current date.
 */
@Action(
    service = Action.Service.BACKEND,
    path = IngestMosApiMetricaReportAction.PATH,
    method = Action.Method.GET,
    auth = Auth.AUTH_ADMIN)
public class IngestMosApiMetricaReportAction implements Runnable {

  public static final String PATH = "/_dr/task/ingestMosApiMetricaReport";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MosApiMetricaService metricaService;
  private final ImmutableSet<String> tlds;

  @Inject
  IngestMosApiMetricaReportAction(
      MosApiMetricaService metricaService, @Config("mosapiTlds") ImmutableSet<String> tlds) {
    this.metricaService = metricaService;
    this.tlds = tlds;
  }

  @Override
  public void run() {
    logger.atInfo().log("Starting MoSAPI Metrica Report Ingestion for TLDs: %s", tlds);

    for (String tld : tlds) {
      try {
        ingestForTld(tld);
      } catch (MosApiException e) {
        logger.atSevere().withCause(e).log("Failed to ingest reports for TLD %s", tld);
      }
    }
  }

  private void ingestForTld(String tld) throws MosApiException {
    Optional<org.joda.time.LocalDate> latestJodaDate = MosApiThreatMatchDao.getLatestCheckDate(tld);
    Optional<LocalDate> latestDate = latestJodaDate.map(d -> LocalDate.of(d.getYear(), d.getMonthOfYear(), d.getDayOfMonth()));

    if (latestDate.isPresent()) {
      LocalDate startDate = latestDate.get().plusDays(1);
      LocalDate endDate = LocalDate.now(ZoneId.of("UTC"));

      if (startDate.isAfter(endDate)) {
        logger.atInfo().log("TLD %s is up to date (latest: %s).", tld, latestDate.get());
        return;
      }

      logger.atInfo().log("Catching up TLD %s from %s to %s", tld, startDate, endDate);
      List<MetricaReportInfo> reports =
          metricaService.listAvailableReports(
              tld, Optional.of(startDate), Optional.of(endDate));

      for (MetricaReportInfo info : reports) {
        processReport(
            metricaService.getReport(tld, Optional.of(info.date())),
            org.joda.time.LocalDate.fromDateFields(java.sql.Date.valueOf(info.date())));
      }
    } else {
      logger.atInfo().log("No existing data for TLD %s. Fetching latest report.", tld);
      MetricaReport report = metricaService.getReport(tld, Optional.empty());
      // Parse date from report string YYYY-MM-DD
      org.joda.time.LocalDate reportDate = org.joda.time.LocalDate.parse(report.getDomainListDate());
      processReport(report, reportDate);
    }
  }

  private void processReport(MetricaReport report, org.joda.time.LocalDate checkDate) {
    logger.atInfo().log(
        "Processing report for TLD %s, Date %s (Threats: %d)",
        report.getTld(), checkDate, report.getThreats().size());

    // Clean up existing entries for this date/TLD (idempotency)
    MosApiThreatMatchDao.deleteEntriesByDateAndTld(checkDate, report.getTld());

    for (MetricaThreatData data : report.getThreats()) {
      // We only ingest named domains.
      if (data.getDomains() != null && !data.getDomains().isEmpty()) {
        for (String domain : data.getDomains()) {
          MosApiThreatMatch match =
              new MosApiThreatMatch.Builder()
                  .setCheckDate(checkDate)
                  .setTld(report.getTld())
                  .setDomainName(domain)
                  .setThreatType(data.getThreatType())
                  .build();
          MosApiThreatMatchDao.save(match);
        }
      } else {
        logger.atInfo().log(
            "Threat type %s has count %d but no named domains. Skipping.",
            data.getThreatType(), data.getCount());
      }
    }
  }
}
