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

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

import static io.imaravic.log4j.logging.GoogleCloudLoggingManager.getManager;

@Plugin(name = "GoogleCloudLogging", category = Node.CATEGORY, elementType = "appender", printObject = true)
public class GoogleCloudLoggingAppender extends AbstractAppender {
  private static final long serialVersionUID = 1L;

  private final GoogleCloudLoggingManager googleCloudLoggingManager;

  protected GoogleCloudLoggingAppender(final String name,
                                       final Filter filter,
                                       final Layout<? extends Serializable> layout,
                                       final boolean ignoreExceptions,
                                       final GoogleCloudLoggingManager googleCloudLoggingManager) {
    super(name, filter, layout, ignoreExceptions);
    this.googleCloudLoggingManager = googleCloudLoggingManager;
  }

  @Override
  public void append(LogEvent event) {
    googleCloudLoggingManager.write(event);
  }

  @PluginBuilderFactory
  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder
      implements org.apache.logging.log4j.core.util.Builder<GoogleCloudLoggingAppender> {
    @PluginElement("Layout")
    private Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();

    @PluginElement("Filter")
    private Filter filter;

    @PluginElement("GoogleCloudCredentials")
    private GoogleCloudCredentials googleCloudCredentials = GoogleCloudCredentials.newBuilder()
        .withComputeCredentials(true)
        .build();

    @PluginBuilderAttribute
    @Required
    private String name;

    @PluginBuilderAttribute
    private boolean ignoreExceptions = true;

    @PluginBuilderAttribute
    private int maxRetryTimeMillis = 500;

    @PluginBuilderAttribute
    private String projectId;

    @PluginBuilderAttribute
    private String zone;

    @PluginBuilderAttribute
    private String virtualMachineId;

    @PluginBuilderAttribute
    private String logName = "cloud.logging.log4j2.appender";

    @Override
    public GoogleCloudLoggingAppender build() {
      try {
        return new GoogleCloudLoggingAppender(name,
                                              filter,
                                              layout,
                                              ignoreExceptions,
                                              getManager(name,
                                                         googleCloudCredentials,
                                                         projectId,
                                                         zone,
                                                         logName,
                                                         virtualMachineId,
                                                         maxRetryTimeMillis));
      } catch (final Throwable e) {
        LOGGER.error("Error creating GoogleCloudLoggingAppender [{}]", name, e);
        return null;
      }
    }
  }
}
