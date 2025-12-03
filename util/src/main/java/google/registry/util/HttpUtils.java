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

package google.registry.util;

import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpUtils {
  /** Private constructor to prevent instantiation. */
  private HttpUtils() {}

  /**
   * Sends an HTTP GET request to the specified URL without any custom headers.
   *
   * @param httpClient the {@link HttpClient} to use for sending the request
   * @param uri the target URI
   * @return the {@link HttpResponse} as a String
   */
  public static HttpResponse<String> sendGetRequest(HttpClient httpClient, URI uri) {
    return sendGetRequest(httpClient, uri, ImmutableMap.of());
  }

  /**
   * Sends an HTTP GET request with custom headers to the specified URL.
   *
   * @param httpClient the {@link HttpClient} to use for sending the request
   * @param uri the target URI
   * @param headers a {@link Map} of header keys and values to add to the request
   * @return the {@link HttpResponse} as a String
   */
  public static HttpResponse<String> sendGetRequest(
      HttpClient httpClient, URI uri, Map<String, String> headers) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri).GET();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }
    return send(httpClient, requestBuilder.build());
  }

  /**
   * Sends an HTTP POST request with an empty body to the specified URL.
   *
   * @param httpClient the {@link HttpClient} to use for sending the request
   * @param uri the target URI
   * @return the {@link HttpResponse} as a String
   */
  public static HttpResponse<String> sendPostRequest(HttpClient httpClient, URI uri) {
    return sendPostRequest(httpClient, uri, ImmutableMap.of());
  }

  /**
   * Sends an HTTP POST request with an empty body and custom headers to the specified URL.
   *
   * @param httpClient the {@link HttpClient} to use for sending the request
   * @param uri the target URL
   * @param headers a {@link Map} of header keys and values to add to the request
   * @return the {@link HttpResponse} as a String
   */
  public static HttpResponse<String> sendPostRequest(
      HttpClient httpClient, URI uri, Map<String, String> headers) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder().uri(uri).POST(HttpRequest.BodyPublishers.noBody());
    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }
    return send(httpClient, requestBuilder.build());
  }

  /**
   * Sends an HTTP POST request with a String body and custom headers to the specified URL.
   *
   * @param httpClient the {@link HttpClient} to use for sending the request
   * @param uri the target URI
   * @param headers a {@link Map} of header keys and values to add to the request
   * @param body the String request body to send (can be null or empty for no body)
   * @return the {@link HttpResponse} as a String
   */
  public static HttpResponse<String> sendPostRequest(
      HttpClient httpClient, URI uri, Map<String, String> headers, String body) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

    if (body == null || body.isEmpty()) {
      requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
    } else {
      requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
    }

    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }
    return send(httpClient, requestBuilder.build());
  }

  /**
   * Sends a pre-built {@link HttpRequest} and handles exceptions.
   *
   * @param httpClient the client
   * @param request the request
   * @return the {@link HttpResponse}
   */
  private static HttpResponse<String> send(HttpClient httpClient, HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
