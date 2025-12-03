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

package google.registry.mosapi.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import google.registry.mosapi.MosApiClient;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.model.MosApiErrorResponse;
import google.registry.mosapi.model.domainmetrica.MetricaReport;
import google.registry.mosapi.model.domainmetrica.MetricaReportInfo;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Response;

/** Facade for MoSAPI's Domain METRICA endpoints. */
public class DomainMetricaClient {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final MosApiClient mosApiClient;
  private final Gson gson;

  @Inject
  public DomainMetricaClient(MosApiClient mosApiClient, Gson gson) {
    this.mosApiClient = mosApiClient;
    this.gson = gson;
  }

  /**
   * Fetches the most recent daily Domain METRICA report.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 9.1</a>
   */
  public MetricaReport getLatestMetricaReport(String tld) throws MosApiException {
    String endpoint = "v2/metrica/domainList/latest";

    try (Response response =
        mosApiClient.sendGetRequest(
            tld, endpoint, Collections.emptyMap(), Collections.emptyMap())) {
      // Return 404 if no report exists for the TLD.
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        throw MosApiException.create(
            gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }
      if (!response.isSuccessful()) {
        throw new MosApiException(
            "Unexpected response code: " + response.code(), new RuntimeException());
      }
      return gson.fromJson(response.body().charStream(), MetricaReport.class);
    } catch (IOException e) {
      throw new MosApiException("Failed to read response body", e);
    }
  }

  /**
   * Fetches the Domain METRICA report for a specific date.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 9.2</a>
   */
  public MetricaReport getMetricaReportForDate(String tld, LocalDate date) throws MosApiException {
    String formattedDate = date.format(DATE_FORMATTER);
    String endpoint = String.format("v2/metrica/domainList/%s", formattedDate);

    try (Response response =
        mosApiClient.sendGetRequest(
            tld, endpoint, Collections.emptyMap(), Collections.emptyMap())) {
      // Return 404 if no report exists for the specified date.
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        throw MosApiException.create(
            gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }
      if (!response.isSuccessful()) {
        throw new MosApiException(
            "Unexpected response code: " + response.code(), new RuntimeException());
      }
      return gson.fromJson(response.body().charStream(), MetricaReport.class);
    } catch (IOException e) {
      throw new MosApiException("Failed to read response body", e);
    }
  }

  /**
   * Lists available Domain METRICA report dates within a given range.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 9.3</a>
   */
  public List<MetricaReportInfo> listAvailableMetricaReports(
      String tld, @Nullable LocalDate startDate, @Nullable LocalDate endDate)
      throws MosApiException {
    String endpoint = "v2/metrica/domainLists";
    ImmutableMap.Builder<String, String> params = new ImmutableMap.Builder<>();
    // Optional startDate and endDate parameters
    if (startDate != null) {
      params.put("startDate", startDate.format(DATE_FORMATTER));
    }
    if (endDate != null) {
      params.put("endDate", endDate.format(DATE_FORMATTER));
    }

    try (Response response =
        mosApiClient.sendGetRequest(tld, endpoint, params.build(), Collections.emptyMap())) {
      // Handle HTTP 400 for specific business logic errors (codes 2012, 2013, 2014)
      if (response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
        MosApiErrorResponse error =
            gson.fromJson(response.body().charStream(), MosApiErrorResponse.class);
        throw MosApiException.create(error);
      }

      if (response.code() != HttpURLConnection.HTTP_OK) {
        throw MosApiException.create(
            gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }

      MetricaReportListResponse listResponse =
          gson.fromJson(response.body().charStream(), MetricaReportListResponse.class);
      return listResponse.domainLists();
    } catch (IOException e) {
      throw new MosApiException("Failed to read response body", e);
    }
  }

  /** Helper class for deserializing the response from the 'domainLists' endpoint. */
  private static class MetricaReportListResponse {
    @Expose
    @SerializedName("domainLists")
    private List<MetricaReportInfo> domainLists;

    List<MetricaReportInfo> domainLists() {
      return domainLists;
    }
  }
}
