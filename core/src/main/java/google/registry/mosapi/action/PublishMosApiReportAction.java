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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.groups.GmailClient;
import google.registry.model.mosapi.MosApiThreatMatch;
import google.registry.model.mosapi.MosApiThreatMatchDao;
import google.registry.request.Action;
import google.registry.request.auth.Auth;
import google.registry.util.EmailMessage;
import jakarta.inject.Inject;
import jakarta.mail.internet.InternetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.joda.time.LocalDate;

/**
 * Action to publish MoSAPI abuse reports via email.
 *
 * <p>This action gathers the latest threat matches for each TLD and sends a consolidated email report
 * to the configured abuse email address.
 */
@Action(
    service = Action.Service.BACKEND,
    path = PublishMosApiReportAction.PATH,
    method = Action.Method.GET,
    auth = Auth.AUTH_ADMIN)
public class PublishMosApiReportAction implements Runnable {

  public static final String PATH = "/_dr/task/publishMosApiReport";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GmailClient gmailClient;
  private final InternetAddress recipient;
  private final ImmutableSet<String> tlds;

  @Inject
  PublishMosApiReportAction(
      GmailClient gmailClient,
      @Config("mosapiAbuseEmailAddress") InternetAddress recipient,
      @Config("mosapiTlds") ImmutableSet<String> tlds) {
    this.gmailClient = gmailClient;
    this.recipient = recipient;
    this.tlds = tlds;
  }

  @Override
  public void run() {
    logger.atInfo().log("Starting MoSAPI Report Publishing for TLDs: %s", tlds);

    StringBuilder emailBody = new StringBuilder();
    emailBody.append("<html><body>");
    emailBody.append("<h1>MoSAPI Abuse Report</h1>");
    emailBody.append("<p>This report contains the latest domain abuse data detected by MoSAPI.</p>");

    boolean hasData = false;

    for (String tld : tlds) {
      Optional<LocalDate> latestDate = MosApiThreatMatchDao.getLatestCheckDate(tld);
      if (latestDate.isEmpty()) {
        logger.atInfo().log("No data found for TLD %s", tld);
        continue;
      }

      ImmutableList<MosApiThreatMatch> matches =
          MosApiThreatMatchDao.loadEntriesByDateAndTld(latestDate.get(), tld);

      if (matches.isEmpty()) {
        continue;
      }

      hasData = true;
      appendTldSection(emailBody, tld, latestDate.get(), matches);
    }

    emailBody.append("</body></html>");

    if (hasData) {
      sendEmail(emailBody.toString());
    } else {
      logger.atInfo().log("No new MoSAPI threats found to report.");
    }
  }

  private void appendTldSection(
      StringBuilder sb, String tld, LocalDate date, ImmutableList<MosApiThreatMatch> matches) {
    sb.append(String.format("<h2>Report for TLD: .%s (Date: %s)</h2>", tld, date));

    // Group by threat type
    Map<String, List<MosApiThreatMatch>> byType =
        matches.stream().collect(Collectors.groupingBy(MosApiThreatMatch::getThreatType));

    for (Map.Entry<String, List<MosApiThreatMatch>> entry : byType.entrySet()) {
      sb.append(String.format("<h3>Threat Type: %s (%d domains)</h3>", entry.getKey(), entry.getValue().size()));
      sb.append("<ul>");
      for (MosApiThreatMatch match : entry.getValue()) {
        // Obfuscate domain to prevent email filters from flagging the report
        String safeDomain = match.getDomainName().replace(".", "[.]");
        sb.append(String.format("<li>%s</li>", safeDomain));
      }
      sb.append("</ul>");
    }
    sb.append("<hr>");
  }

  private void sendEmail(String body) {
    try {
      gmailClient.sendEmail(
          EmailMessage.newBuilder()
              .setSubject("Daily MoSAPI Abuse Report")
              .setBody(body)
              .setContentType(MediaType.HTML_UTF_8)
              .addRecipient(recipient)
              .build());
      logger.atInfo().log("Sent MoSAPI report to %s", recipient);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Failed to send MoSAPI report email.");
      throw new RuntimeException(e);
    }
  }
}
