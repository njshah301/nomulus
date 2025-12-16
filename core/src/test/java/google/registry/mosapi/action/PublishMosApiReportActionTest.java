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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import google.registry.groups.GmailClient;
import google.registry.model.mosapi.MosApiThreatMatch;
import google.registry.model.mosapi.MosApiThreatMatchDao;
import google.registry.persistence.transaction.JpaTestRules;
import google.registry.persistence.transaction.JpaTestRules.JpaIntegrationTestExtension;
import google.registry.util.EmailMessage;
import jakarta.mail.internet.InternetAddress;
import org.joda.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link PublishMosApiReportAction}. */
public class PublishMosApiReportActionTest {

  @RegisterExtension
  public final JpaIntegrationTestExtension jpa =
      new JpaTestRules.Builder().buildIntegrationTestExtension();

  private final GmailClient gmailClient = mock(GmailClient.class);
  private final InternetAddress recipient = new InternetAddress();
  private final String tld = "test";
  private PublishMosApiReportAction action;

  @BeforeEach
  void setUp() {
    recipient.setAddress("abuse@example.com");
    action = new PublishMosApiReportAction(gmailClient, recipient, ImmutableSet.of(tld));
  }

  @Test
  void testRun_noData_sendsNothing() {
    action.run();
    verify(gmailClient, never()).sendEmail(any());
  }

  @Test
  void testRun_hasData_sendsEmail() throws Exception {
    LocalDate today = LocalDate.now();
    MosApiThreatMatch match = new MosApiThreatMatch.Builder()
        .setTld(tld)
        .setCheckDate(today)
        .setDomainName("bad.test")
        .setThreatType("malware")
        .build();
    MosApiThreatMatchDao.save(match);

    action.run();

    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(gmailClient).sendEmail(captor.capture());
    
    EmailMessage message = captor.getValue();
    assertThat(message.subject()).isEqualTo("Daily MoSAPI Abuse Report");
    assertThat(message.recipients()).containsExactly(recipient);
    assertThat(message.body()).contains("Report for TLD: .test");
    assertThat(message.body()).contains("malware");
    assertThat(message.body()).contains("bad[.]test");
  }
}
