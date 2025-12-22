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

package google.registry.mosapi.model;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TldServiceState}. */
public class TldServiceStateTest {

  private final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

  @Test
  void testConstructorAndGetters_allFieldsPopulated() {
    ServiceStatus dnsStatus = new ServiceStatus("Up", 0.0, Collections.emptyList());
    Map<String, ServiceStatus> services = ImmutableMap.of("DNS", dnsStatus);

    TldServiceState state = new TldServiceState("example.tld", 123456L, "Up", services);

    assertThat(state.tld()).isEqualTo("example.tld");
    assertThat(state.lastUpdateApiDatabase()).isEqualTo(123456L);
    assertThat(state.status()).isEqualTo("Up");
    assertThat(state.serviceStatuses()).containsEntry("DNS", dnsStatus);
  }

  @Test
  void testJsonSerialization() {
    ServiceStatus rddsStatus = new ServiceStatus("Down", 100.0, ImmutableList.of());
    Map<String, ServiceStatus> services = ImmutableMap.of("RDDS", rddsStatus);

    TldServiceState state = new TldServiceState("test.tld", 99999L, "Down", services);

    String json = gson.toJson(state);

    // Verify annotated fields are present
    assertThat(json)
        .contains(
            """
            "tld":"test.tld"\
            """
                .trim());
    assertThat(json)
        .contains(
            """
            "status":"Down"\
            """
                .trim());
    assertThat(json)
        .contains(
            """
            "testedServices":\
            """);
    assertThat(json)
        .contains(
            """
            "RDDS":\
            """);

    // Verify unannotated field (lastUpdateApiDatabase) is EXCLUDED
    assertThat(json).doesNotContain("lastUpdateApiDatabase");
    assertThat(json).doesNotContain("99999");
  }

  @Test
  void testJsonDeserialization() {
    String json =
        """
        {
          "tld": "example.tld",
          "status": "Up",
          "lastUpdateApiDatabase": 55555,
          "testedServices": {
            "EPP": {
              "status": "Up",
              "emergencyThreshold": 0.0,
              "incidents": []
            }
          }
        }
        """;

    TldServiceState state = gson.fromJson(json, TldServiceState.class);

    assertThat(state.tld()).isEqualTo("example.tld");
    assertThat(state.status()).isEqualTo("Up");

    // Check map deserialization
    assertThat(state.serviceStatuses()).containsKey("EPP");
    assertThat(state.serviceStatuses().get("EPP").status()).isEqualTo("Up");

    assertThat(state.lastUpdateApiDatabase()).isEqualTo(0L);
  }
}
