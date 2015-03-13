Log4j2 Appender For Google Cloud Logging
========================================

A log4j2 appender to publish logs directly to [Google Cloud Logging](https://cloud.google.com/logging/docs/).

All the calls to Google Cloud Logging are blocking, 
so it's a good idea to combine this appender with AsyncAppender, or with AsyncLogger.

In case this appender is combined with either AsyncAppender or AsyncLogger it performs batching,
which is controlled from either AsyncAppender or AsyncLogger;

Usage
-----

### In `log4j2.xml`

If the Java Application is run from Google Cloud machine (Appengine, Dataflow or Compute), 
all the metadata, including credentials can be read from `metadata` service.
Please note, that the machine should be created with scope `logging-write` 
so it would be able to send logs.

In that case Appender config is as simple as possible.
```xml
<Appenders>
  <GoogleCloudLogging name="gcloud_logging_from_gce"/>
</Appenders>
```

In case the Java Application is not run from Google Cloud machine,
or if you just want to control all params manually and/or use ServiceAccount credentials
config is a bit more complicated.
```xml
<Appenders>
  <GoogleCloudLogging name="gcloud_logging_not_from_gce"
                      projectId="gcloud-projectId"
                      zone="europe-west1-d"
                      virtualMachineId="0">
    <GoogleCloudCredentials
        serviceAccountId="serviceId@developer.gserviceaccount.com"
        serviceAccountPrivateKeyP12FileName="file.p12"/>
  </GoogleCloudLogging>
</Appenders>
```

`projectId` represents id of Google Cloud project to which the logs should be published,
and for which the credentials are valid.

`zone` represents the Google Cloud zone in which the machine is located.

`virtualMachineId` represents machine unique id in Google Cloud project.

`GoogleCloudCredentials` are set with two required params.

`serviceAccountId` represents service account e-mail address.

`serviceAccountPrivateKeyP12FileName` represents location to service accounts P12 key file on the machine.

### In `pom.xml`

Artifact is still not uploaded to Maven Central.

To try it out, clone it and run 

```bash
mvn install
```

after this you can add following to your `pom.xml`

```xml
<dependency>
  <groupId>io.imaravic.log4j</groupId>
  <artifactId>google-cloud-logging-log4j2-appender</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Beside this log4j2 should be bootstraped for your application too.
