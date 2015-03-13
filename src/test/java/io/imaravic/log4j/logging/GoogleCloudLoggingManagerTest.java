package io.imaravic.log4j.logging;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.services.logging.Logging;
import com.google.api.services.logging.model.LogEntry;
import com.google.api.services.logging.model.WriteLogEntriesRequest;
import com.google.common.collect.ImmutableMap;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.List;

import io.imaravic.log4j.logging.util.GoogleCloudMetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GoogleCloudLoggingManager.class})
@PowerMockIgnore({"javax.management.*", "javax.crypto.*"})
public class GoogleCloudLoggingManagerTest {
  @Mock
  HttpTransport httpTransport;

  @Mock
  GoogleCloudMetadata googleCloudMetadata;

  @Mock
  GoogleCloudCredentials googleCloudCredentials;

  @Mock
  Logging loggingClient;

  @Before
  public void setup() throws Exception {
    PowerMockito.spy(GoogleCloudLoggingManager.class);
    PowerMockito.doReturn(loggingClient).when(GoogleCloudLoggingManager.class,
                                              "createLoggingClient",
                                              any(HttpTransport.class),
                                              any(GoogleCloudCredentials.class),
                                              anyInt());

    when(googleCloudMetadata.fetchFromPath("project/project-id"))
        .thenReturn("project_id");
    when(googleCloudMetadata.fetchFromPath("instance/zone"))
        .thenReturn("projects/55605977627/zones/europe-west1-d");
    when(googleCloudMetadata.fetchFromPath("instance/id"))
        .thenReturn("vm_id");

    when(googleCloudCredentials.usingComputeCredentials())
        .thenReturn(true);
    when(googleCloudCredentials.getServiceAccountId())
        .thenReturn(null);
  }

  @Test
  public void testBootstrappingManagerFromGCEOnCompute() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.000Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    assertEquals(ImmutableMap.of("compute.googleapis.com/resource_type", "instance",
                                 "compute.googleapis.com/resource_id", "vm_id"),
                 writtenLogEntriesCaptor.getValue().getCommonLabels());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals(1, entries.size());

    assertNotNull(entries.get(0).getInsertId());
    assertEquals("LogMsg", entries.get(0).getTextPayload());
    assertEquals("log_name", entries.get(0).getLog());
    assertEquals("project_id", entries.get(0).getMetadata().getProjectId());
    assertEquals("compute.googleapis.com", entries.get(0).getMetadata().getServiceName());
    assertEquals("INFO", entries.get(0).getMetadata().getSeverity());
    assertEquals("2015-04-06T18:38:24.000Z", entries.get(0).getMetadata().getTimestamp());
    assertEquals(null, entries.get(0).getMetadata().getUserId());
    assertEquals("europe-west1-d", entries.get(0).getMetadata().getZone());
  }

  @Test
  public void testBootstrappingManagerFromGCEOnDataflow() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("job_id");
    when(googleCloudMetadata.fetchFromPath("instance/attributes/job_id"))
        .thenReturn("job_id");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    assertEquals(ImmutableMap.of("dataflow.googleapis.com/job_id", "job_id"),
                 writtenLogEntriesCaptor.getValue().getCommonLabels());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals(1, entries.size());

    assertNotNull(entries.get(0).getInsertId());
    assertEquals("LogMsg", entries.get(0).getTextPayload());
    assertEquals("log_name", entries.get(0).getLog());
    assertEquals("project_id", entries.get(0).getMetadata().getProjectId());
    assertEquals("dataflow.googleapis.com", entries.get(0).getMetadata().getServiceName());
    assertEquals("INFO", entries.get(0).getMetadata().getSeverity());
    assertEquals("2015-04-06T18:38:24.002Z", entries.get(0).getMetadata().getTimestamp());
    assertEquals(null, entries.get(0).getMetadata().getUserId());
    assertEquals("europe-west1-d", entries.get(0).getMetadata().getZone());
  }

  @Test
  public void testBootstrappingManagerFromGCEOnAppengine() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("gae_backend_name\ngae_backend_version");
    when(googleCloudMetadata.fetchFromPath("instance/attributes/gae_backend_name"))
        .thenReturn("module_id");
    when(googleCloudMetadata.fetchFromPath("instance/attributes/gae_backend_version"))
        .thenReturn("version_id");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    assertEquals(ImmutableMap.of("compute.googleapis.com/resource_type", "instance",
                                 "compute.googleapis.com/resource_id", "vm_id",
                                 "appengine.googleapis.com/module_id", "module_id",
                                 "appengine.googleapis.com/version_id", "version_id"),
                 writtenLogEntriesCaptor.getValue().getCommonLabels());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals(1, entries.size());

    assertNotNull(entries.get(0).getInsertId());
    assertEquals("LogMsg", entries.get(0).getTextPayload());
    assertEquals("appengine.googleapis.com%2Flog_name", entries.get(0).getLog());
    assertEquals("project_id", entries.get(0).getMetadata().getProjectId());
    assertEquals("appengine.googleapis.com", entries.get(0).getMetadata().getServiceName());
    assertEquals("INFO", entries.get(0).getMetadata().getSeverity());
    assertEquals("2015-04-06T18:38:24.002Z", entries.get(0).getMetadata().getTimestamp());
    assertEquals(null, entries.get(0).getMetadata().getUserId());
    assertEquals("europe-west1-d", entries.get(0).getMetadata().getZone());
  }

  @Test
  public void testBatchingFromManager() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    final int batchSize = 128;
    for (int i = 0; i < batchSize - 1; ++i) {
      googleCloudLoggingManager.write(buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO));
      verify(googleCloudLoggingManager, never())
          .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));
    }

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals(batchSize, entries.size());
  }

  @Test(expected = AppenderLoggingException.class)
  public void testExceptionIsThrownOnExceptionFromLoggingClient() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doThrow(new IOException("TEST")).when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);
  }

  @Test
  public void testLogNameIsUrlEscaped() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "escape/me hurra",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals("escape%2Fme+hurra", entries.get(0).getLog());
  }

  @Test
  public void testWarnLevelGetsTranslatedToWarningSeverity() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.WARN);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals("WARNING", entries.get(0).getMetadata().getSeverity());
  }

  @Test
  public void testFatalLevelGetsTranslatedToCriticalSeverity() throws Exception {
    when(googleCloudMetadata.fetchFromPath("instance/attributes/"))
        .thenReturn("");

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       null,
                                                       null,
                                                       "log_name",
                                                       null,
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.002Z", Level.FATAL);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals("CRITICAL", entries.get(0).getMetadata().getSeverity());
  }

  @Test
  public void testBootstrappingManagerNotFromGCE() throws Exception {
    when(googleCloudCredentials.usingComputeCredentials())
        .thenReturn(false);
    when(googleCloudCredentials.getServiceAccountId())
        .thenReturn("user@gcloud.com");

    when(googleCloudMetadata.fetchFromPath(anyString()))
        .thenThrow(new IOException("TEST"));

    GoogleCloudLoggingManager googleCloudLoggingManager =
        PowerMockito.spy(new GoogleCloudLoggingManager("name",
                                                       httpTransport,
                                                       googleCloudMetadata,
                                                       googleCloudCredentials,
                                                       "_project_id_",
                                                       "_zone_",
                                                       "log_name",
                                                       "_vm_id_",
                                                       1));

    doNothing().when(googleCloudLoggingManager)
        .writeToGoogleCloudLogging(any(WriteLogEntriesRequest.class));

    LogEvent event = buildLogEvent("LogMsg", "2015-04-06T18:38:24.000Z", Level.INFO);
    event.setEndOfBatch(true);

    googleCloudLoggingManager.write(event);

    ArgumentCaptor<WriteLogEntriesRequest> writtenLogEntriesCaptor =
        ArgumentCaptor.forClass(WriteLogEntriesRequest.class);
    verify(googleCloudLoggingManager).writeToGoogleCloudLogging(writtenLogEntriesCaptor.capture());

    assertEquals(ImmutableMap.of("compute.googleapis.com/resource_type", "instance",
                                 "compute.googleapis.com/resource_id", "_vm_id_"),
                 writtenLogEntriesCaptor.getValue().getCommonLabels());

    List<LogEntry> entries = writtenLogEntriesCaptor.getValue().getEntries();
    assertEquals(1, entries.size());

    assertNotNull(entries.get(0).getInsertId());
    assertEquals("LogMsg", entries.get(0).getTextPayload());
    assertEquals("log_name", entries.get(0).getLog());
    assertEquals("_project_id_", entries.get(0).getMetadata().getProjectId());
    assertEquals("compute.googleapis.com", entries.get(0).getMetadata().getServiceName());
    assertEquals("INFO", entries.get(0).getMetadata().getSeverity());
    assertEquals("2015-04-06T18:38:24.000Z", entries.get(0).getMetadata().getTimestamp());
    assertEquals("user@gcloud.com", entries.get(0).getMetadata().getUserId());
    assertEquals("_zone_", entries.get(0).getMetadata().getZone());
  }

  private static Log4jLogEvent buildLogEvent(final String logMsg,
                                             final String timestamp,
                                             final Level level) {
    return Log4jLogEvent.createEvent("loggerName",
                                     null,
                                     "loggerFQCN",
                                     level,
                                     new SimpleMessage(logMsg),
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     new DateTime(timestamp)
                                         .getValue());
  }
}