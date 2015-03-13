package io.imaravic.log4j.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GoogleCloudLoggingManager.class})
@PowerMockIgnore({"javax.management.*", "javax.crypto.*"})
public class GoogleCloudLoggingAppenderTest {

  @Test
  public void testGoogleCloudLoggingAppenderBuilderWithDefaultsGetsBootstrappedSuccessfully() throws Exception {
    mockStatic(GoogleCloudLoggingManager.class);
    final GoogleCloudLoggingManager googleCloudLoggingManager = mock(GoogleCloudLoggingManager.class);
    when(GoogleCloudLoggingManager.getManager(anyString(),
                                              any(GoogleCloudCredentials.class),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyInt())).thenReturn(googleCloudLoggingManager);

    final GoogleCloudLoggingAppender appender = GoogleCloudLoggingAppender.newBuilder().build();

    final LogEvent logEvent = mock(LogEvent.class);
    appender.append(logEvent);

    verify(googleCloudLoggingManager).write(eq(logEvent));
  }

  @Test
  public void testGoogleCloudLoggingAppenderBuilderInCaseOfAnExceptionReturnsNull() throws Exception {
    mockStatic(GoogleCloudLoggingManager.class);
    when(GoogleCloudLoggingManager.getManager(anyString(),
                                              any(GoogleCloudCredentials.class),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyInt())).thenThrow(new RuntimeException("TEST"));

    final GoogleCloudLoggingAppender appender = GoogleCloudLoggingAppender.newBuilder().build();
    assertEquals(null, appender);
  }
}