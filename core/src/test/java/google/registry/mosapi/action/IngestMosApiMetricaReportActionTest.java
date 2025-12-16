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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.persistence.transaction.TransactionManagerFactory.tm;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.mosapi.MosApiThreatMatch;
import google.registry.model.mosapi.MosApiThreatMatchDao;
import google.registry.mosapi.dto.domainmetrica.MetricaReport;
import google.registry.mosapi.dto.domainmetrica.MetricaReportInfo;
import google.registry.mosapi.dto.domainmetrica.MetricaThreatData;
import google.registry.mosapi.services.MosApiMetricaService;
import google.registry.persistence.transaction.JpaTestRules;
import google.registry.persistence.transaction.JpaTestRules.JpaIntegrationTestExtension;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Unit tests for {@link IngestMosApiMetricaReportAction}. */
public class IngestMosApiMetricaReportActionTest {

  @RegisterExtension
  public final JpaIntegrationTestExtension jpa =
      new JpaTestRules.Builder().buildIntegrationTestExtension();

  private final MosApiMetricaService metricaService = mock(MosApiMetricaService.class);
  private IngestMosApiMetricaReportAction action;
  private final String tld = "test";

  @BeforeEach
  void setUp() {
    action = new IngestMosApiMetricaReportAction(metricaService, ImmutableSet.of(tld));
  }

  @Test
  void testRun_firstRun_fetchesLatest() throws Exception {
    LocalDate reportDate = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
    MetricaReport report = createReport(reportDate, "malware", "example.test");
    
    when(metricaService.getReport(tld, Optional.empty())).thenReturn(report);

    action.run();

    ImmutableList<MosApiThreatMatch> results = MosApiThreatMatchDao.loadEntriesByDate(org.joda.time.LocalDate.fromDateFields(java.sql.Date.valueOf(reportDate)));
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getDomainName()).isEqualTo("example.test");
  }

  @Test
  void testRun_catchUp_fetchesMissingReports() throws Exception {
    // Seed initial data for T-3
    LocalDate tMinus3 = LocalDate.now(ZoneId.of("UTC")).minusDays(3);
    MosApiThreatMatch existing = new MosApiThreatMatch.Builder()
        .setTld(tld)
        .setCheckDate(org.joda.time.LocalDate.fromDateFields(java.sql.Date.valueOf(tMinus3)))
        .setDomainName("old.test")
        .setThreatType("spam")
        .build();
    MosApiThreatMatchDao.save(existing);
    
    // Setup Service to return lists for T-2 and T-1
    LocalDate tMinus2 = LocalDate.now(ZoneId.of("UTC")).minusDays(2);
    LocalDate tMinus1 = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
    
    MetricaReportInfo info1 = mock(MetricaReportInfo.class);
    when(info1.date()).thenReturn(tMinus2);
    MetricaReportInfo info2 = mock(MetricaReportInfo.class);
    when(info2.date()).thenReturn(tMinus1);
    
    when(metricaService.listAvailableReports(eq(tld), any(), any()))
        .thenReturn(ImmutableList.of(info1, info2));
        
    when(metricaService.getReport(tld, Optional.of(tMinus2)))
        .thenReturn(createReport(tMinus2, "botnet", "bot.test"));
    when(metricaService.getReport(tld, Optional.of(tMinus1)))
        .thenReturn(createReport(tMinus1, "phishing", "phish.test"));

    action.run();
    
    // Verify T-2 ingested
    assertThat(MosApiThreatMatchDao.loadEntriesByDate(org.joda.time.LocalDate.fromDateFields(java.sql.Date.valueOf(tMinus2)))).hasSize(1);
    // Verify T-1 ingested
    assertThat(MosApiThreatMatchDao.loadEntriesByDate(org.joda.time.LocalDate.fromDateFields(java.sql.Date.valueOf(tMinus1)))).hasSize(1);
  }
  
  private MetricaReport createReport(LocalDate date, String type, String domain) {
      MetricaReport report = mock(MetricaReport.class);
      MetricaThreatData data = mock(MetricaThreatData.class);
      when(data.getThreatType()).thenReturn(type);
      when(data.getDomains()).thenReturn(ImmutableList.of(domain));
      when(data.getCount()).thenReturn(1);
      
      when(report.getTld()).thenReturn(tld);
      when(report.getDomainListDate()).thenReturn(date.toString());
      when(report.getThreats()).thenReturn(ImmutableList.of(data));
      return report;
  }
}
