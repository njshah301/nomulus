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

package google.registry.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.reporting.mosapi.MosApiClient;
import jakarta.inject.Inject;

@Parameters(
    commandNames = "mosapi_start",
    commandDescription = "Start a MoSAPI session (login) for a given TLD.")

public class MosapiStartCommand implements  Command{
  @Inject MosApiClient mosApiClient;

  @Parameter(
      names = "--tld",
      description = "The TLD to log in for (e.g., .app, .dev).",
      required = true)
  private String tld;

  @Override
  public void run() {
    System.out.printf("Attempting MoSAPI login for TLD: %s...%n", tld);
    try {
      mosApiClient.login(tld);
      System.out.println("✅ Login successful.");
    } catch (Exception e) {
      System.err.printf("❌ An unexpected error occurred during login for %s:%n", tld);
      e.printStackTrace(System.err);
    }
  }

}

