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

import com.google.gson.Gson;
import google.registry.mosapi.MosApiClient;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.model.MosApiErrorResponse;
import google.registry.mosapi.model.servicemonitoring.ServiceAlarm;
import google.registry.mosapi.model.servicemonitoring.ServiceDowntime;
import google.registry.mosapi.model.servicemonitoring.TldServiceState;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import okhttp3.Response;

/** Facade for MoSAPI's service monitoring endpoints. */
public class ServiceMonitoringClient {
  private final MosApiClient mosApiClient;
  private final Gson gson;

  @Inject
  public ServiceMonitoringClient(MosApiClient mosApiClient, Gson gson) {
    this.mosApiClient = mosApiClient;
    this.gson = gson;
  }

  /**
   * Fetches the current state of all monitored services for a given TLD.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 5.1</a>
   */
  public TldServiceState getServiceState(String tld) throws MosApiException {
    String endpoint = "v2/monitoring/state";
    try (Response response =
        mosApiClient.sendGetRequest(
            tld, endpoint, Collections.emptyMap(), Collections.emptyMap())) {
      if (!response.isSuccessful()) {
        throw MosApiException.create(
            gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }
      return gson.fromJson(response.body().charStream(), TldServiceState.class);
    } catch (IOException e) {
      throw new MosApiException("Failed to read service state response", e);
    }
  }

  /**
   * Fetches the total downtime for a specific service over a rolling week period.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 5.3</a>
   */
  public ServiceDowntime getDowntime(String tld, String service) throws MosApiException {
    String endpoint = String.format("v2/monitoring/%s/downtime", service);
    try (Response response =
        mosApiClient.sendGetRequest(
            tld, endpoint, Collections.emptyMap(), Collections.emptyMap())) {
      switch (response.code()) {
        case HttpURLConnection.HTTP_OK:
          return gson.fromJson(response.body().charStream(), ServiceDowntime.class);
        case HttpURLConnection.HTTP_NOT_FOUND:
          return new ServiceDowntime(2, 0, 0, true);
        default:
          throw MosApiException.create(
              gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }
    } catch (IOException e) {
      throw new MosApiException("Failed to read downtime response", e);
    }
  }

  /**
   * Checks if a specific service has an active alarm.
   *
   * @see <a href="https://www.icann.org/mosapi-specification.pdf">ICANN MoSAPI Specification,
   *     Section 5.2</a>
   */
  public ServiceAlarm serviceAlarmed(String tld, String service) throws MosApiException {
    String endpoint = String.format("v2/monitoring/%s/alarmed", service);
    try (Response response =
        mosApiClient.sendGetRequest(
            tld, endpoint, Collections.emptyMap(), Collections.emptyMap())) {

      switch (response.code()) {
        case HttpURLConnection.HTTP_OK:
          return gson.fromJson(response.body().charStream(), ServiceAlarm.class);
        case HttpURLConnection.HTTP_NOT_FOUND:
          return new ServiceAlarm(2, 0, "Disabled");
        default:
          throw MosApiException.create(
              gson.fromJson(response.body().charStream(), MosApiErrorResponse.class));
      }
    } catch (IOException e) {
      throw new MosApiException("Failed to read alarm response", e);
    }
  }
}
