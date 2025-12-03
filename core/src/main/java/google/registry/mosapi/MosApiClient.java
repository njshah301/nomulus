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

package google.registry.mosapi;

import google.registry.config.RegistryConfig.Config;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.exception.MosApiException.MosApiAuthorizationException;
import google.registry.util.HttpUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class MosApiClient {

  private final HttpClient httpClient;
  private final String baseUrl;

  @Inject
  public MosApiClient(
      @Named("mosapiHttpClient") HttpClient httpClient,
      @Config("mosapiServiceUrl") String mosapiUrl,
      @Config("mosapiEntityType") String entityType) {
    this.httpClient = httpClient;
    this.baseUrl = String.format("%s/%s", mosapiUrl, entityType);
  }

  /**
   * Sends a GET request to the specified MoSAPI endpoint.
   *
   * @param entityId The TLD or registrar ID the request is for.
   * @param endpoint The specific API endpoint path (e.g., "v2/monitoring/state").
   * @param params A map of query parameters to be URL-encoded and appended to the request.
   * @param headers A map of HTTP headers to be included in the request.
   * @return The {@link HttpResponse} from the server if the request is successful.
   * @throws MosApiException if the request fails due to a network error or an unhandled HTTP
   *     status.
   * @throws MosApiAuthorizationException if the server returns a 401 Unauthorized status.
   */
  public HttpResponse<String> sendGetRequest(
      String entityId, String endpoint, Map<String, String> params, Map<String, String> headers)
      throws MosApiException {
    URI uri = buildUri(entityId, endpoint, params);
    try {
      HttpResponse<String> response = HttpUtils.sendGetRequest(httpClient, uri, headers);
      return checkResponseForAuthError(response);
    } catch (RuntimeException e) {
      throw new MosApiException("Error during GET request to " + uri.getPath(), e);
    }
  }

  /**
   * Sends a POST request to the specified MoSAPI endpoint.
   *
   * <p><b>Note:</b> This method is for future use. There are currently no MoSAPI endpoints in the
   * project scope that require a POST request.
   *
   * @param entityId The TLD or registrar ID the request is for.
   * @param endpoint The specific API endpoint path.
   * @param params A map of query parameters to be URL-encoded.
   * @param headers A map of HTTP headers to be included in the request.
   * @param body The request body to be sent with the POST request.
   * @return The {@link HttpResponse} from the server.
   * @throws MosApiException if the request fails.
   * @throws MosApiAuthorizationException if the server returns a 401 Unauthorized status.
   */
  public HttpResponse<String> sendPostRequest(
      String entityId,
      String endpoint,
      Map<String, String> params,
      Map<String, String> headers,
      String body)
      throws MosApiException {
    URI uri = buildUri(entityId, endpoint, params);
    try {
      HttpResponse<String> response = HttpUtils.sendPostRequest(httpClient, uri, headers, body);
      return checkResponseForAuthError(response);
    } catch (RuntimeException e) {
      throw new MosApiException("Error during POST request to " + uri.getPath(), e);
    }
  }

  private HttpResponse<String> checkResponseForAuthError(HttpResponse<String> response)
      throws MosApiAuthorizationException {
    if (response.statusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
      throw new MosApiAuthorizationException(
          "Authorization failed for the requested resource. The client certificate may not be"
              + " authorized for the specified TLD or Registrar.");
    }
    return response;
  }

  /**
   * Builds the full URL for a request, including the base URL, entityId, path, and query params.
   */
  private URI buildUri(String entityId, String path, Map<String, String> queryParams) {
    String sanitizedPath = path.startsWith("/") ? path : "/" + path;
    String fullPath = baseUrl + "/" + entityId + sanitizedPath;

    if (queryParams == null || queryParams.isEmpty()) {
      return URI.create(fullPath);
    }
    String queryString =
        queryParams.entrySet().stream()
            .map(
                entry ->
                    entry.getKey()
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return URI.create(fullPath + "?" + queryString);
  }
}
