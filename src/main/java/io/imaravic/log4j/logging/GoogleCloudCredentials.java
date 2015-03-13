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

package io.imaravic.log4j.logging;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.common.annotations.VisibleForTesting;

import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;

@Plugin(name = "googleCloudCredentials", category = Node.CATEGORY, printObject = true)
public class GoogleCloudCredentials {
  private final boolean useComputeCredentials;
  private final String serviceAccountId;
  private final File serviceAccountPrivateKeyP12File;

  protected GoogleCloudCredentials(final boolean useComputeCredentials,
                                   final String serviceAccountId,
                                   final File serviceAccountPrivateKeyP12File) {
    this.useComputeCredentials = useComputeCredentials;
    this.serviceAccountId = serviceAccountId;
    this.serviceAccountPrivateKeyP12File = serviceAccountPrivateKeyP12File;
  }

  public Credential getCredential(final HttpTransport transport,
                                  final JacksonFactory jacksonFactory,
                                  final Collection<String> serviceAccountScopes)
      throws GeneralSecurityException, IOException {
    if (useComputeCredentials) {
      return buildNewComputeCredentials(transport, jacksonFactory);
    }
    return buildNewGoogleCredentials(transport, jacksonFactory, serviceAccountScopes);
  }

  public boolean usingComputeCredentials() {
    return useComputeCredentials;
  }

  public String getServiceAccountId() {
    return serviceAccountId;
  }

  @VisibleForTesting
  String getServiceAccountPrivateKeyP12FileName() {
    return serviceAccountPrivateKeyP12File.getName();
  }

  @VisibleForTesting
  GoogleCredential buildNewGoogleCredentials(final HttpTransport transport,
                                             final JacksonFactory jacksonFactory,
                                             final Collection<String> serviceAccountScopes)
      throws GeneralSecurityException, IOException {
    return new GoogleCredential.Builder()
        .setTransport(transport)
        .setJsonFactory(jacksonFactory)
        .setServiceAccountScopes(serviceAccountScopes)
        .setServiceAccountId(serviceAccountId)
        .setServiceAccountPrivateKeyFromP12File(serviceAccountPrivateKeyP12File)
        .build();
  }

  @VisibleForTesting
  ComputeCredential buildNewComputeCredentials(final HttpTransport transport,
                                               final JacksonFactory jacksonFactory) {
    return new ComputeCredential.Builder(transport, jacksonFactory).build();
  }

  @PluginFactory
  public static GoogleCloudCredentials createGoogleCloudCredentials(
      @PluginAttribute(value = "serviceAccountId") final String serviceAccountId,
      @PluginAttribute(value = "serviceAccountPrivateKeyP12FileName") final String serviceAccountPrivateKeyP12FileName) {
    return newBuilder()
        .withServiceAccountId(serviceAccountId)
        .withServiceAccountPrivateKeyP12FileName(serviceAccountPrivateKeyP12FileName)
        .build();
  }

  @VisibleForTesting
  static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder
      implements org.apache.logging.log4j.core.util.Builder<GoogleCloudCredentials> {
    private Boolean useComputeCredentials;
    private String serviceAccountId;
    private String serviceAccountPrivateKeyP12FileName;

    public Builder withComputeCredentials(final boolean useComputeCredentials) {
      this.useComputeCredentials = useComputeCredentials;
      return this;
    }

    public Builder withServiceAccountId(final String serviceAccountId) {
      this.serviceAccountId = serviceAccountId;
      return this;
    }

    public Builder withServiceAccountPrivateKeyP12FileName(final String serviceAccountPrivateKeyP12FileName) {
      this.serviceAccountPrivateKeyP12FileName = serviceAccountPrivateKeyP12FileName;
      return this;
    }

    @Override
    public GoogleCloudCredentials build() {
      File serviceAccountPrivateKeyP12File = null;
      if (useComputeCredentials == null || !useComputeCredentials) {
        Preconditions.checkArgument(serviceAccountId != null,
                                    "ServiceAccountID must be set " +
                                    "if useComputeCredentials is not set or set to false");
        Preconditions.checkArgument(serviceAccountPrivateKeyP12FileName != null,
                                   "ServiceAccountID must be set " +
                                   "if useComputeCredentials is not set or set to false");
        serviceAccountPrivateKeyP12File = getServiceAccountPrivateKeyP12File();
      } else {
        Preconditions.checkArgument(serviceAccountId == null,
                                    "ServiceAccountID must not be set " +
                                    "if useComputeCredentials is set to true");
        Preconditions.checkArgument(serviceAccountPrivateKeyP12FileName == null,
                                    "ServiceAccountPrivateKeyP12FileName must not be set " +
                                    "if useComputeCredentials is set to true");
      }
      return new GoogleCloudCredentials(useComputeCredentials == null ? false : useComputeCredentials,
                                        serviceAccountId,
                                        serviceAccountPrivateKeyP12File);
    }

    @VisibleForTesting
    File getServiceAccountPrivateKeyP12File() {
      return new File(serviceAccountPrivateKeyP12FileName);
    }
  }
}
