/*
 * Copyright (c) 2015 Igor Maravić
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.imaravic.log4j.logging.util;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;

import java.io.IOException;

public class GoogleCloudMetadata {
  private static final String COMPUTE_METADATA_BASE_URL = "http://metadata/computeMetadata/v1/";

  private final HttpTransport transport;

  public GoogleCloudMetadata(final HttpTransport transport) {
    this.transport = transport;
  }

  public String fetchFromPath(final String path) throws IOException {
    final GenericUrl metadataUrl = new GenericUrl(COMPUTE_METADATA_BASE_URL);
    metadataUrl.appendRawPath(path);
    HttpRequest request = transport.createRequestFactory()
        .buildGetRequest(metadataUrl);
    request.getHeaders().set("Metadata-Flavor", "Google");
    return request.execute().parseAsString();
  }
}
