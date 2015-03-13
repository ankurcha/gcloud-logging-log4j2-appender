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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.logging.Logging;
import com.google.api.services.logging.LoggingScopes;
import com.google.api.services.logging.model.LogEntry;
import com.google.api.services.logging.model.LogEntryMetadata;
import com.google.api.services.logging.model.WriteLogEntriesRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.util.UuidUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import io.imaravic.log4j.logging.util.GoogleCloudMetadata;
import io.imaravic.log4j.logging.util.RetryHttpInitializerWrapper;

public class GoogleCloudLoggingManager extends AbstractManager {
  private static final String APPLICATION_NAME = "GoogleCloudLogging-Log4j2Appender";

  private static final String COMPUTE_SERVICE_NAME = "compute.googleapis.com";
  private static final String APPENGINE_SERVICE_NAME = "appengine.googleapis.com";
  private static final String DATAFLOW_SERVICE_NAME = "dataflow.googleapis.com";

  private List<LogEntry> logEntriesBuffer = Lists.newArrayList();
  private final String googleCloudProjectId;
  private final String googleCloudZone;
  private final String googleCloudLogName;
  private final String serviceName;
  private final GoogleCloudCredentials googleCloudCredentials;
  private final Logging loggingClient;
  private final ImmutableMap<String, String> commonLabels;

  @VisibleForTesting
  GoogleCloudLoggingManager(final String name,
                            final HttpTransport transport,
                            final GoogleCloudMetadata googleCloudMetadata,
                            final GoogleCloudCredentials googleCloudCredentials,
                            final String googleCloudProjectId,
                            final String googleCloudZone,
                            String googleCloudLogName,
                            final String virtualMachineId,
                            final int maxRetryTimeMillis)
      throws GeneralSecurityException, IOException {
    super(name);

    this.googleCloudProjectId = getGoogleCloudProjectId(googleCloudProjectId, googleCloudMetadata);
    this.googleCloudZone = getGoogleCloudZone(googleCloudZone, googleCloudMetadata);

    if (!googleCloudCredentials.usingComputeCredentials()) {
      serviceName = COMPUTE_SERVICE_NAME;
      commonLabels = getComputeServiceCommonLabels(
          getVirtualMachineId(virtualMachineId, googleCloudMetadata));
    } else {
      final List<String> machineAttributes =
          getMachineAttributes(googleCloudMetadata);
      final ImmutableMap.Builder<String, String> commonLabelsBuilder = ImmutableMap.builder();

      if (machineAttributes.contains("gae_backend_name") &&
          machineAttributes.contains("gae_backend_version")) {
        serviceName = APPENGINE_SERVICE_NAME;

        // Add a prefix to Appengine logs to prevent namespace collisions
        googleCloudLogName = APPENGINE_SERVICE_NAME + "/" + googleCloudLogName;

        commonLabelsBuilder.putAll(getAppengineServiceCommonLabels(googleCloudMetadata));
      } else if (machineAttributes.contains("job_id")) {
        serviceName = DATAFLOW_SERVICE_NAME;

        commonLabelsBuilder.putAll(getDataflowServiceCommonLabels(googleCloudMetadata));
      } else {
        serviceName = COMPUTE_SERVICE_NAME;
      }

      if (!serviceName.equals(DATAFLOW_SERVICE_NAME)) {
        commonLabelsBuilder
            .putAll(getComputeServiceCommonLabels(
                getVirtualMachineId(virtualMachineId, googleCloudMetadata)));
      }
      commonLabels = commonLabelsBuilder.build();
    }

    this.googleCloudLogName = URLEncoder.encode(googleCloudLogName, "UTF-8");
    this.googleCloudCredentials = googleCloudCredentials;
    this.loggingClient = createLoggingClient(transport,
                                             googleCloudCredentials,
                                             maxRetryTimeMillis);
  }

  public synchronized void write(final LogEvent event) {
    final String logMsg = event.getMessage().getFormattedMessage();
    final String timestamp = new DateTime(event.getTimeMillis(), 0).toStringRfc3339();
    final String severity = log4j2LevelToCloudLoggingLevel(event.getLevel());
    final String insertId = UuidUtil.getTimeBasedUuid().toString();

    final LogEntry entry = new LogEntry();
    final LogEntryMetadata entryMetadata = new LogEntryMetadata();

    entry.setTextPayload(logMsg)
        .setLog(googleCloudLogName)
        .setInsertId(insertId)
        .setMetadata(entryMetadata.setProjectId(googleCloudProjectId)
                         .setServiceName(serviceName)
                         .setSeverity(severity)
                         .setTimestamp(timestamp)
                         .setUserId(googleCloudCredentials.getServiceAccountId())
                         .setZone(googleCloudZone));
    logEntriesBuffer.add(entry);

    if (event.isEndOfBatch()) {
      final List<LogEntry> entriesToWrite = logEntriesBuffer;
      logEntriesBuffer = Lists.newArrayList();

      final WriteLogEntriesRequest writeLogEntriesRequest =
          new WriteLogEntriesRequest().setEntries(entriesToWrite).setCommonLabels(commonLabels);
      try {
        writeToGoogleCloudLogging(writeLogEntriesRequest);
      } catch (final IOException e) {
        throw new AppenderLoggingException("Sending message to projectId " +
                                           "\"" + googleCloudProjectId + "\" " +
                                           "and logName \"" + googleCloudLogName + "\" failed", e);
      }
    }
  }

  @VisibleForTesting
  void writeToGoogleCloudLogging(final WriteLogEntriesRequest writeLogEntriesRequest)
      throws IOException {
    loggingClient.projects()
        .logs()
        .entries()
        .write(googleCloudProjectId,
               googleCloudLogName,
               writeLogEntriesRequest)
        .execute();
  }

  public static GoogleCloudLoggingManager getManager(final String name,
                                                     final GoogleCloudCredentials googleCloudCredentials,
                                                     final String googleCloudProjectId,
                                                     final String googleCloudZone,
                                                     final String googleCloudLogName,
                                                     final String virtualMachineId,
                                                     final int maxRetryTimeMillis) {
    return AbstractManager.getManager(
        name,
        new ManagerFactory<GoogleCloudLoggingManager, Object>() {
          @Override
          public GoogleCloudLoggingManager createManager(String name,
                                                         Object data) {
            try {
              final HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
              final GoogleCloudMetadata googleCloudMetadata = new GoogleCloudMetadata(transport);
              return new GoogleCloudLoggingManager(name,
                                                   transport,
                                                   googleCloudMetadata,
                                                   googleCloudCredentials,
                                                   googleCloudProjectId,
                                                   googleCloudZone,
                                                   googleCloudLogName,
                                                   virtualMachineId,
                                                   maxRetryTimeMillis);
            } catch (final Throwable e) {
              LOGGER.error("Failed to initialize GoogleCloudLoggingManager", e);
            }
            return null;
          }
        },
        null);
  }

  private static List<String> getMachineAttributes(final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    return Arrays.asList(googleCloudMetadata.fetchFromPath("instance/attributes/").split("\n"));
  }

  private static ImmutableMap<String, String>
  getAppengineServiceCommonLabels(final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    return ImmutableMap.of(APPENGINE_SERVICE_NAME + "/module_id",
                           googleCloudMetadata.fetchFromPath("instance/attributes/gae_backend_name"),
                           APPENGINE_SERVICE_NAME + "/version_id",
                           googleCloudMetadata.fetchFromPath("instance/attributes/gae_backend_version"));
  }

  private static ImmutableMap<String, String>
  getDataflowServiceCommonLabels(final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    return ImmutableMap.of(DATAFLOW_SERVICE_NAME + "/job_id",
                           googleCloudMetadata.fetchFromPath("instance/attributes/job_id"));
  }

  private static ImmutableMap<String, String>
  getComputeServiceCommonLabels(final String virtualMachineId) {
    return ImmutableMap.of(COMPUTE_SERVICE_NAME + "/resource_type", "instance",
                           COMPUTE_SERVICE_NAME + "/resource_id", virtualMachineId);
  }

  private static String getVirtualMachineId(final String virtualMachineId,
                                            final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    if (virtualMachineId == null) {
      return googleCloudMetadata.fetchFromPath("instance/id");
    } else {
      return virtualMachineId;
    }
  }

  private static String getGoogleCloudZone(final String googleCloudZone,
                                           final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    if (googleCloudZone == null) {
      final String[] fullyQualifiedZone =
          googleCloudMetadata.fetchFromPath("instance/zone").split("/");
      return fullyQualifiedZone[fullyQualifiedZone.length - 1];
    } else {
      return googleCloudZone;
    }
  }

  private static String getGoogleCloudProjectId(final String googleCloudProjectId,
                                                final GoogleCloudMetadata googleCloudMetadata)
      throws IOException {
    if (googleCloudProjectId == null) {
      return googleCloudMetadata.fetchFromPath("project/project-id");
    } else {
      return googleCloudProjectId;
    }
  }

  private static Logging createLoggingClient(final HttpTransport transport,
                                             final GoogleCloudCredentials credentials,
                                             final int maxRetryTimeMillis)
      throws GeneralSecurityException, IOException {
    final JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
    return new Logging.Builder(transport,
                               jacksonFactory,
                               new RetryHttpInitializerWrapper(
                                   credentials.getCredential(transport,
                                                             jacksonFactory,
                                                             LoggingScopes.all()),
                                   maxRetryTimeMillis))
        .setApplicationName(APPLICATION_NAME)
        .build();
  }

  private static String log4j2LevelToCloudLoggingLevel(final Level level) {
    if (level == Level.WARN) {
      return "WARNING";
    } else if (level == Level.FATAL) {
      return "CRITICAL";
    }
    return level.toString();
  }
}
