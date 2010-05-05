/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package com.google.code.camel.smtp;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.net.smtp.SMTPClient;
import org.junit.Test;

public class SMTPTest extends CamelTestSupport{

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("smtp:localhost:2525").to("mock:result");
            }
        };
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        String sender = "sender@localhost";
        String rcpt = "rcpt@localhost";
        String body = "Subject: test\r\n\r\nTestmail";
        SMTPClient client = new SMTPClient();
        client.connect("localhost", 2525);
        client.helo("localhost");
        client.setSender(sender);
        client.addRecipient(rcpt);
        
        client.sendShortMessageData(body);
        client.quit();
        client.disconnect();
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodyReceived().body(InputStream.class);
        Map<String, Object> headers = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeaders();
        assertEquals(sender, headers.get(MailEnvelopeMessage.SMTP_SENDER_ADRRESS));
        assertEquals(rcpt, headers.get(MailEnvelopeMessage.SMTP_RCPT_ADRRESS_LIST));
        resultEndpoint.assertIsSatisfied();
    }

}
