/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.notification.publisher;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;

@QuarkusTest
@TestProfile(SlackPublisherWithoutBaseUrlTest.TestProfile.class)
public class SlackPublisherWithoutBaseUrlTest extends AbstractWebhookPublisherTest<SlackPublisher> {

    public static class TestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("dtrack.general.base.url", "")
            );
        }
    }

    @Override
    JsonObjectBuilder extraConfig() {
        return super.extraConfig()
                .add(Publisher.CONFIG_DESTINATION, "http://localhost:" + wireMockPort);
    }

    @Test
    @TestTransaction
    public void testInformWithNewVulnerabilityNotificationWithoutBaseUrl() throws Exception {
            super.testInformWithNewVulnerabilityNotification();

            wireMock.verifyThat(postRequestedFor(anyUrl())
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""
                        {
                          "blocks": [
                            {
                              "type": "header",
                              "text": {
                                "type": "plain_text",
                                "text": "New Vulnerability"
                              }
                            },
                            {
                              "type": "context",
                              "elements": [
                                {
                                  "text": "*LEVEL_INFORMATIONAL*  |  *SCOPE_PORTFOLIO*",
                                  "type": "mrkdwn"
                                }
                              ]
                            },
                            {
                              "type": "divider"
                            },
                            {
                              "type": "section",
                              "text": {
                                "text": "New Vulnerability Identified",
                                "type": "mrkdwn"
                              },
                              "fields": [
                                {
                                  "type": "mrkdwn",
                                  "text": "*VulnID*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "INT-001"
                                },
                                {
                                  "type": "mrkdwn",
                                  "text": "*Severity*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "MEDIUM"
                                },
                                {
                                  "type": "mrkdwn",
                                  "text": "*Source*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "INTERNAL"
                                },
                                {
                                  "type": "mrkdwn",
                                  "text": "*Component*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "componentName : componentVersion"
                                }
                              ]
                            }
                          ]
                        }
                        """)));
        }

    @Test
    @TestTransaction
    public void testInformWithNewVulnerableDependencyNotificationWithoutBaseUrl() throws Exception {
        super.testInformWithNewVulnerableDependencyNotification();

        wireMock.verifyThat(postRequestedFor(anyUrl())
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {
                          "blocks": [
                            {
                              "type": "header",
                              "text": {
                                "type": "plain_text",
                                "text": "New Vulnerable Dependency"
                              }
                            },
                            {
                              "type": "context",
                              "elements": [
                                {
                                  "text": "*LEVEL_INFORMATIONAL*  |  *SCOPE_PORTFOLIO*",
                                  "type": "mrkdwn"
                                }
                              ]
                            },
                            {
                              "type": "divider"
                            },
                            {
                              "type": "section",
                              "text": {
                                "text": "Vulnerable Dependency Introduced",
                                "type": "mrkdwn"
                              },
                              "fields": [
                                {
                                  "type": "mrkdwn",
                                  "text": "*Component*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "componentName : componentVersion"
                                },
                                {
                                  "type": "mrkdwn",
                                  "text": "*Project*"
                                },
                                {
                                  "type": "plain_text",
                                  "text": "pkg:maven/org.acme/projectName@projectVersion"
                                }
                              ]
                            }
                          ]
                        }
                        """)));
    }

    @Test
    @TestTransaction
    public void testInformWithProjectAuditChangeNotificationWithoutBaseUrl() throws Exception {
        super.testInformWithProjectAuditChangeNotification();

        wireMock.verifyThat(postRequestedFor(anyUrl())
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {
                          "blocks": [
                            {
                        	  "type": "header",
                        	  "text": {
                        	    "type": "plain_text",
                        		"text": "Project Audit Change"
                        	  }
                        	},
                        	{
                        	  "type": "context",
                        	  "elements": [
                        	    {
                        		  "text": "*LEVEL_INFORMATIONAL*  |  *SCOPE_PORTFOLIO*",
                        		  "type": "mrkdwn"
                        		}
                        	  ]
                        	},
                        	{
                        	  "type": "divider"
                        	},
                        	{
                        	  "type": "section",
                        	  "text": {
                        	    "text": "Analysis Decision: Finding Suppressed",
                        		"type": "plain_text"
                        	  },
                        	  "fields": [
                        	    {
                        		  "type": "mrkdwn",
                        		  "text": "*Analysis State*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "emoji": true,
                        		  "text": "FALSE_POSITIVE"
                        		},
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*Suppressed*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "true"
                        		},
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*VulnID*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "INT-001"
                        		},
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*Severity*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "MEDIUM"
                        		},
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*Source*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "INTERNAL"
                        		}
                        	  ]
                        	},
                            {
                        	  "type": "section",
                        	  "fields": [
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*Component*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "componentName : componentVersion"
                        		},
                        		{
                        		  "type": "mrkdwn",
                        		  "text": "*Project*"
                        		},
                        		{
                        		  "type": "plain_text",
                        		  "text": "pkg:maven/org.acme/projectName@projectVersion"
                        		}
                        	  ]
                        	}
                          ]
                        }
                        """)));
    }
}
