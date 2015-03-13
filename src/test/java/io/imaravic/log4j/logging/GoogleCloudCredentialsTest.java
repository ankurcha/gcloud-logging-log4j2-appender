package io.imaravic.log4j.logging;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.logging.LoggingScopes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GoogleCloudCredentials.class})
@PowerMockIgnore({"javax.management.*", "javax.crypto.*"})
public class GoogleCloudCredentialsTest {
  @Spy
  GoogleCloudCredentials.Builder googleCloudCredentialsBuilder =
      new GoogleCloudCredentials.Builder();

  @Before
  public void setup() throws Exception {
    doReturn(mock(File.class))
        .when(googleCloudCredentialsBuilder)
        .getServiceAccountPrivateKeyP12File();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFactoryMethodWithServiceAccountIdSetAndNullP12FileNameThrows() throws Exception {
    PowerMockito.spy(GoogleCloudCredentials.class);
    PowerMockito.doReturn(googleCloudCredentialsBuilder).when(GoogleCloudCredentials.class,
                                                              "newBuilder");
    GoogleCloudCredentials.createGoogleCloudCredentials("serviceId", null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFactoryMethodWithP12FileNameSetAndNullServiceAccountIdThrows() throws Exception {
    PowerMockito.spy(GoogleCloudCredentials.class);
    PowerMockito.doReturn(googleCloudCredentialsBuilder).when(GoogleCloudCredentials.class,
                                                              "newBuilder");
    GoogleCloudCredentials.createGoogleCloudCredentials(null, "p12File");
  }

  @Test
  public void testGoogleCloudCredentialsReturnComputeCredentialsWhenSetToUseComputeCredentials() throws Exception {
    final GoogleCloudCredentials cloudCredentials =
        PowerMockito.spy(googleCloudCredentialsBuilder.withComputeCredentials(true).build());

    doReturn(mock(ComputeCredential.class)).when(cloudCredentials)
        .buildNewComputeCredentials(any(HttpTransport.class),
                                    any(JacksonFactory.class));
    doReturn(mock(GoogleCredential.class)).when(cloudCredentials)
        .buildNewGoogleCredentials(any(HttpTransport.class),
                                   any(JacksonFactory.class),
                                   anyCollection());

    final HttpTransport transport = mock(HttpTransport.class);
    cloudCredentials.getCredential(transport,
                                   JacksonFactory.getDefaultInstance(),
                                   LoggingScopes.all());

    verify(cloudCredentials).buildNewComputeCredentials(any(HttpTransport.class),
                                                        eq(JacksonFactory.getDefaultInstance()));
    verify(cloudCredentials, never()).buildNewGoogleCredentials(any(HttpTransport.class),
                                                                any(JacksonFactory.class),
                                                                anyCollection());

    assertTrue(cloudCredentials.usingComputeCredentials());
    assertTrue(cloudCredentials.getServiceAccountId() == null);
  }

  @Test
  public void testGoogleCloudCredentialsReturnGoogleCredentialsWhenSetWithPrivateKeyInfo() throws Exception {
    final GoogleCloudCredentials cloudCredentials =
        PowerMockito.spy(googleCloudCredentialsBuilder.withServiceAccountId("serviceId")
                             .withServiceAccountPrivateKeyP12FileName("p12File")
                             .build());

    doReturn(mock(ComputeCredential.class)).when(cloudCredentials)
        .buildNewComputeCredentials(any(HttpTransport.class),
                                    any(JacksonFactory.class));
    doReturn(mock(GoogleCredential.class)).when(cloudCredentials)
        .buildNewGoogleCredentials(any(HttpTransport.class),
                                   any(JacksonFactory.class),
                                   anyCollection());

    cloudCredentials.getCredential(mock(HttpTransport.class),
                                   JacksonFactory.getDefaultInstance(),
                                   LoggingScopes.all());

    verify(cloudCredentials, never()).buildNewComputeCredentials(any(HttpTransport.class),
                                                                 any(JacksonFactory.class));
    verify(cloudCredentials).buildNewGoogleCredentials(any(HttpTransport.class),
                                                       eq(JacksonFactory.getDefaultInstance()),
                                                       eq(LoggingScopes.all()));

    assertFalse(cloudCredentials.usingComputeCredentials());
    assertEquals("serviceId", cloudCredentials.getServiceAccountId());
  }
}