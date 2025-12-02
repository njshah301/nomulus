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

package google.registry.mosapi.module;

import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.privileges.secretmanager.SecretManagerClient;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Module
public final class MosApiModule {
  /**
   * Provides a Provider for the MoSAPI TLS Cert.
   *
   * <p>This method returns a Dagger {@link Provider} that can be used to fetch the TLS Certs for a
   * MosAPI.
   *
   * @param secretManagerClient The injected Secret Manager client.
   * @return A Provider for the MoSAPI TLS Certs.
   */
  @Provides
  @Named("mosapiTlsCert")
  public static String provideMosapiTlsCert(SecretManagerClient secretManagerClient) {
    return secretManagerClient.getSecretData(
        "nomulus-dot-foo_tls-client-dot-crt-dot-pem", Optional.of("latest"));
  }

  /**
   * Provides a Provider for the MoSAPI TLS Key.
   *
   * <p>This method returns a Dagger {@link Provider} that can be used to fetch the TLS Key for a
   * MosAPI.
   *
   * @param secretManagerClient The injected Secret Manager client.
   * @return A Provider for the MoSAPI TLS Key.
   */
  @Provides
  @Named("mosapiTlsKey")
  public static String provideMosapiTlsKey(SecretManagerClient secretManagerClient) {
    return secretManagerClient.getSecretData(
        "nomulus-dot-foo_tls-client-dot-key", Optional.of("latest"));
  }

  /**
   * Provides a fixed thread pool for parallel TLD processing.
   *
   * <p>Strictly bound to 4 threads to comply with MoSAPI session limits (4 concurrent sessions per
   * certificate). This is used by MosApiStateService to fetch data in parallel.
   *
   * @see <a href="go/mosapi-design">Design Doc Section 2.1.2</a>
   */
  @Provides
  @Singleton
  @Named("mosapiTldExecutor")
  static ExecutorService provideMosapiTldExecutor(
      @Config("mosapiTldThreadCnt") int threadPoolSize) {
    return Executors.newFixedThreadPool(threadPoolSize);
  }

  /**
   * Provides a thread pool for asynchronous metrics exportation.
   *
   * <p>This supports the "Fire-and-Forget" pattern. We use a fixed pool of size 4 to match the TLD
   * processing concurrency, ensuring that metric exporting does not bottleneck the system.
   */
  @Provides
  @Singleton
  @Named("mosapiMetricsExecutor")
  static ExecutorService provideMosapiMetricsExecutor(
      @Config("mosapiMetricsThreadCnt") int threadPoolSize) {
    return Executors.newFixedThreadPool(threadPoolSize);
  }
}
