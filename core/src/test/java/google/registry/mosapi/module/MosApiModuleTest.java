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
package google.registry.mosapi.module;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import google.registry.privileges.secretmanager.SecretManagerClient;
import java.net.http.HttpClient;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link MosApiModule}. */
@ExtendWith(MockitoExtension.class)
public class MosApiModuleTest {

  @Mock private SecretManagerClient secretManagerClient;
  @Mock private SSLContext mockSslContext;

  @Test
  void testProvideMosapiTlsCert_returnsSecret() {
    String secretName = "nomulus-dot-foo_tls-client-dot-crt-dot-pem";
    String fakeCert = "FAKE_CERT";
    when(secretManagerClient.getSecretData(secretName, Optional.of("latest"))).thenReturn(fakeCert);
    String result = MosApiModule.provideMosapiTlsCert(secretManagerClient);
    assertThat(result).isEqualTo(fakeCert);
    verify(secretManagerClient).getSecretData(secretName, Optional.of("latest"));
  }

  @Test
  void testProvideMosapiTlsKey_returnsSecret() {
    String secretName = "nomulus-dot-foo_tls-client-dot-key";
    String fakeKey = "FAKE_KEY";
    when(secretManagerClient.getSecretData(secretName, Optional.of("latest"))).thenReturn(fakeKey);
    String result = MosApiModule.provideMosapiTlsKey(secretManagerClient);
    assertThat(result).isEqualTo(fakeKey);
    verify(secretManagerClient).getSecretData(secretName, Optional.of("latest"));
  }

  @Test
  void testProvidePrivateKey_throwsOnBadKey() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class, () -> MosApiModule.providePrivateKey("not a real key"));
    assertThat(thrown).hasMessageThat().contains("Could not parse TLS private key");
  }

  @Test
  void testProvideCertificate_throwsOnBadCert() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class, () -> MosApiModule.provideCertificate("not a real cert"));
    assertThat(thrown).hasMessageThat().contains("Could not create X.509 certificate");
  }

  @Test
  void testProvideMosapiHttpClient_usesSslContext() {
    HttpClient client = MosApiModule.provideMosapiHttpClient(mockSslContext);
    assertThat(client).isNotNull();
    assertThat(client.sslContext()).isEqualTo(mockSslContext);
  }
}
