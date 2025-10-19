// Copyright 2025 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpUtilsTest {
  @Mock private HttpClient mockHttpClient;
  @Mock private HttpResponse<String> mockHttpResponse;
  @Captor private ArgumentCaptor<HttpRequest> requestCaptor;

  @Test
  void sendGetRequest_success_returnsResponse() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);
    HttpResponse<String> response = HttpUtils.sendGetRequest(mockHttpClient, "https://example.com");
    assertThat(response).isSameInstanceAs(mockHttpResponse);
  }

  @Test
  void sendPostRequest_success_returnsResponse() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);
    HttpResponse<String> response =
        HttpUtils.sendPostRequest(mockHttpClient, "https://example.com");
    assertThat(response).isSameInstanceAs(mockHttpResponse);
  }

  @Test
  void sendPostRequest_withHeaders_headersAreSet() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);
    HttpUtils.sendPostRequest(
        mockHttpClient, "https://example.com", ImmutableMap.of("Authorization", "Basic 12345"));
    verify(mockHttpClient).send(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
        .hasValue("Basic 12345");
  }

  @Test
  void send_ioException_throwsIOException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Network failed"));
    IOException e =
        assertThrows(
            IOException.class,
            () -> HttpUtils.sendGetRequest(mockHttpClient, "https://example.com"));
    assertThat(e).hasMessageThat().isEqualTo("Network failed");
  }

  @Test
  void send_interruptedException_throwsInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("Request interrupted"));
    InterruptedException e =
        assertThrows(
            InterruptedException.class,
            () -> HttpUtils.sendGetRequest(mockHttpClient, "https://example.com"));
    assertThat(e).hasMessageThat().isEqualTo("Request interrupted");
  }
}
