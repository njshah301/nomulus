// Copyright 2025 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package google.registry.mosapi;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.mosapi.exception.MosApiException;
import google.registry.mosapi.exception.MosApiException.MosApiAuthorizationException;
import google.registry.util.HttpUtils;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link MosApiClient}. */
@ExtendWith(MockitoExtension.class)
public class MosApiClientTest {

  private static final String MOSAPI_URL = "https://mosapi.example.com";
  private static final String ENTITY_TYPE = "tld";
  private static final String BASE_URL = MOSAPI_URL + "/" + ENTITY_TYPE;
  private static final String ENTITY_ID = "example";

  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;

  private MockedStatic<HttpUtils> httpUtilsMock;
  private MosApiClient client;

  @BeforeEach
  void setUp() {
    httpUtilsMock = mockStatic(HttpUtils.class);
    client = new MosApiClient(httpClient, MOSAPI_URL, ENTITY_TYPE);
  }

  @AfterEach
  void tearDown() {
    httpUtilsMock.close();
  }

  @Test
  void sendGetRequest_success() throws Exception {
    String endpoint = "v2/check";
    Map<String, String> params = ImmutableMap.of();
    Map<String, String> headers = ImmutableMap.of("Authorization", "Bearer token");
    URI expectedUri = URI.create(BASE_URL + "/" + ENTITY_ID + "/" + endpoint);

    when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_OK);
    when(httpResponse.body()).thenReturn("Success");

    httpUtilsMock
        .when(() -> HttpUtils.sendGetRequest(eq(httpClient), eq(expectedUri), eq(headers)))
        .thenReturn(httpResponse);

    HttpResponse<String> result = client.sendGetRequest(ENTITY_ID, endpoint, params, headers);

    assertThat(result).isEqualTo(httpResponse);
    assertThat(result.body()).isEqualTo("Success");
  }

  @Test
  void sendGetRequest_withQueryParams_buildsCorrectUrl() throws Exception {
    String endpoint = "v2/search";
    Map<String, String> params = ImmutableMap.of("q", "foo bar", "limit", "10");
    Map<String, String> headers = ImmutableMap.of();

    when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_OK);
    httpUtilsMock
        .when(() -> HttpUtils.sendGetRequest(eq(httpClient), any(URI.class), eq(headers)))
        .thenAnswer(
            invocation -> {
              URI uri = invocation.getArgument(1);
              assertThat(uri.getPath())
                  .isEqualTo("/" + ENTITY_TYPE + "/" + ENTITY_ID + "/" + endpoint);
              assertThat(uri.getQuery()).contains("q=foo+bar");
              assertThat(uri.getQuery()).contains("limit=10");
              return httpResponse;
            });

    client.sendGetRequest(ENTITY_ID, endpoint, params, headers);
  }

  @Test
  void sendGetRequest_unauthorized_throwsException() {
    when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_UNAUTHORIZED);
    httpUtilsMock
        .when(() -> HttpUtils.sendGetRequest(any(), any(URI.class), anyMap()))
        .thenReturn(httpResponse);

    MosApiAuthorizationException thrown =
        assertThrows(
            MosApiAuthorizationException.class,
            () -> client.sendGetRequest(ENTITY_ID, "test", ImmutableMap.of(), ImmutableMap.of()));

    assertThat(thrown).hasMessageThat().contains("Authorization failed");
  }

  @Test
  void sendGetRequest_runtimeException_wrapsInMosApiException() {
    RuntimeException networkError = new RuntimeException("Connection timeout");
    httpUtilsMock
        .when(() -> HttpUtils.sendGetRequest(any(), any(URI.class), anyMap()))
        .thenThrow(networkError);

    MosApiException thrown =
        assertThrows(
            MosApiException.class,
            () -> client.sendGetRequest(ENTITY_ID, "test", ImmutableMap.of(), ImmutableMap.of()));

    assertThat(thrown).hasMessageThat().contains("Error during GET request");
    assertThat(thrown).hasCauseThat().isEqualTo(networkError);
  }

  @Test
  void sendPostRequest_success() throws Exception {
    String endpoint = "v2/update";
    String body = "{\"key\":\"value\"}";
    URI expectedUri = URI.create(BASE_URL + "/" + ENTITY_ID + "/" + endpoint);

    when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_CREATED);

    httpUtilsMock
        .when(() -> HttpUtils.sendPostRequest(eq(httpClient), eq(expectedUri), anyMap(), eq(body)))
        .thenReturn(httpResponse);

    HttpResponse<String> result =
        client.sendPostRequest(ENTITY_ID, endpoint, null, ImmutableMap.of(), body);

    assertThat(result.statusCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);
  }

  @Test
  void sendPostRequest_unauthorized_throwsException() {
    when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_UNAUTHORIZED);
    httpUtilsMock
        .when(() -> HttpUtils.sendPostRequest(any(), any(URI.class), anyMap(), any(String.class)))
        .thenReturn(httpResponse);

    assertThrows(
        MosApiAuthorizationException.class,
        () -> client.sendPostRequest(ENTITY_ID, "test", null, ImmutableMap.of(), "body"));
  }

  @Test
  void sendPostRequest_runtimeException_wrapsException() {
    httpUtilsMock
        .when(() -> HttpUtils.sendPostRequest(any(), any(URI.class), anyMap(), any(String.class)))
        .thenThrow(new RuntimeException("Network error"));

    MosApiException thrown =
        assertThrows(
            MosApiException.class,
            () -> client.sendPostRequest(ENTITY_ID, "test", null, ImmutableMap.of(), "body"));

    assertThat(thrown).hasMessageThat().contains("Error during POST request");
  }
}
