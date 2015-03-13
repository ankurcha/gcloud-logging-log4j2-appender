package io.imaravic.log4j.logging;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GoogleCloudLoggingManager.class})
@PowerMockIgnore({"javax.management.*", "javax.crypto.*"})
public class GoogleCloudLoggingIntegrationFromGceTest {
  @Before
  public void setup() throws Exception {
    mockStatic(GoogleCloudLoggingManager.class);
    final GoogleCloudLoggingManager googleCloudLoggingManager = mock(GoogleCloudLoggingManager.class);
    when(GoogleCloudLoggingManager.getManager(anyString(),
                                              any(GoogleCloudCredentials.class),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyString(),
                                              anyInt())).thenReturn(googleCloudLoggingManager);
  }

  @Test
  public void testSettingCloudLoggingFromGce() throws Exception {
    LoggerFactory.getLogger("gcloud_logging_from_gce");
    ArgumentCaptor<GoogleCloudCredentials> credentialsCaptor =
        ArgumentCaptor.forClass(GoogleCloudCredentials.class);

    verifyStatic();
    GoogleCloudLoggingManager.getManager(eq("gcloud_logging_from_gce"),
                                         credentialsCaptor.capture(),
                                         isNull(String.class),
                                         isNull(String.class),
                                         anyString(),
                                         isNull(String.class),
                                         anyInt());
    assertTrue(credentialsCaptor.getValue().usingComputeCredentials());
  }
}
