/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2016 The ZAP development team
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
 */
package org.zaproxy.zap.extension.ascanrulesBeta;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Plugin.AttackStrength;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Unit test for {@link SourceCodeDisclosureCVE20121823}.
 */
public class SourceCodeDisclosureCVE20121823UnitTest extends ActiveScannerTest {

    private static final String RESPONSE_HEADER_404_NOT_FOUND = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n";
    private static final String PHP_SOURCE_TAGS = "<?php $x=0; echo '<h1>Welcome!</h1>'; ?>";
    private static final String PHP_SOURCE_ECHO_TAG = "<?= '<h1>Welcome!</h1>' ?>";

    @Override
    protected SourceCodeDisclosureCVE20121823 createScanner() {
        SourceCodeDisclosureCVE20121823 scanner = new SourceCodeDisclosureCVE20121823();
        scanner.setConfig(new ZapXmlConfiguration());
        return scanner;
    }

    @Test
    public void shouldIgnore404NotFoundResponsesOnMediumAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.MEDIUM);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
    }

    @Test
    public void shouldIgnore404NotFoundResponsesOnLowAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.LOW);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
    }

    @Test
    public void shouldScan404NotFoundResponsesOnHighAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.setAttackStrength(AttackStrength.HIGH);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    public void shouldScan404NotFoundResponsesOnInsaneAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.INSANE);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    public void shouldScanUrlsWithoutPath() throws Exception {
        // Given
        nano.addHandler(new NanoServerHandler("shouldScanUrlsWithoutPath") {

            @Override
            Response serve(IHTTPSession session) {
                return new Response("No Source Code here!");
            }
        });
        HttpMessage message = getHttpMessage("");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    public void shouldScanUrlsWithEncodedCharsInPath() throws Exception {
        // Given
        String test = "shouldScanUrlsWithEncodedCharsInPath";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                return new Response("No Source Code here!");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/%7B+%25%24");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    public void shouldNotAlertIfThereIsNoSourceCodeDisclosure() throws Exception {
        // Given
        String test = "shouldNotAlertIfThereIsNoSourceCodeDisclosure";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                return new Response("No Source Code here!");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    public void shouldAlertIfPhpSourceTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "shouldAlertIfPhpSourceTagsWereDisclosedInResponseBody";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_TAGS);
                return new Response("<html><body>" + encodedPhpCode + "</body></html>");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(1));
        assertThat(alertsRaised.get(0).getEvidence(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getParam(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getAttack(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alertsRaised.get(0).getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alertsRaised.get(0).getOtherInfo(), is(equalTo(PHP_SOURCE_TAGS)));
    }

    @Test
    public void shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpSourceTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpSourceTagsWereDisclosedInResponseBody";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_TAGS);
                return new Response(
                        Response.Status.INTERNAL_ERROR,
                        "text/html",
                        "<html><body>" + encodedPhpCode + "</body></html>");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    public void shouldAlertIfPhpEchoTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "shouldAlertIfPhpEchoTagsWereDisclosedInResponseBody";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                return new Response("<html><body>" + encodedPhpCode + "</body></html>");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(1));
        assertThat(alertsRaised.get(0).getEvidence(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getParam(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getAttack(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alertsRaised.get(0).getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alertsRaised.get(0).getOtherInfo(), is(equalTo(PHP_SOURCE_ECHO_TAG)));
    }

    @Test
    public void shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpEchoTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpEchoTagsWereDisclosedInResponseBody";
        nano.addHandler(new NanoServerHandler(test) {

            @Override
            Response serve(IHTTPSession session) {
                String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                return new Response(
                        Response.Status.INTERNAL_ERROR,
                        "text/html",
                        "<html><body>" + encodedPhpCode + "</body></html>");
            }
        });
        HttpMessage message = getHttpMessage("/" + test + "/");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
        assertThat(alertsRaised, hasSize(0));
    }

    private HttpMessage httpMessage404NotFound() throws Exception {
        HttpMessage message = getHttpMessage("/");
        message.setResponseHeader(RESPONSE_HEADER_404_NOT_FOUND);
        return message;
    }
}
