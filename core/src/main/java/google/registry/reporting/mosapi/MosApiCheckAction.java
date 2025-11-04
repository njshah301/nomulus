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


package google.registry.reporting.mosapi;

import static javax.security.auth.callback.ConfirmationCallback.OK;

import com.google.common.net.MediaType;
import google.registry.module.mosapi.MosApiRequestComponent;
import google.registry.request.Action;
import google.registry.request.Action.GaeService;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import jakarta.inject.Inject;

/**
 * A simple check action for the MoSAPI module.
 *
 * <p>This path is mapped to this action by Dagger because this class is returned by a
 * method in {@link MosApiRequestComponent}. The security level (AUTH_ADMIN)
 * is enforced by the {@link google.registry.request.auth.AuthModule}.
 */
@Action(
    service = GaeService.MOSAPI,
    path = "/mosapi/check", // This is the path mapping
    method = Action.Method.GET,
    auth = Auth.AUTH_ADMIN) // Requires admin login, per web.xml
public class MosApiCheckAction implements Runnable{
  @Inject Response response;

  @Inject
  MosApiCheckAction() {}

  @Override
  public void run() {
    response.setStatus(OK);
    response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
    response.setPayload("Hello from Registry Mosapi test check");
  }
}
