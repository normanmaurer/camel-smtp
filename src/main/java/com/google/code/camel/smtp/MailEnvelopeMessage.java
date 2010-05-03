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

import java.util.Iterator;

import org.apache.camel.impl.DefaultMessage;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.mailet.MailAddress;

public class MailEnvelopeMessage extends DefaultMessage{

    public final static String SMTP_SENDER_ADRRESS = "SMTP_SENDER_ADDRESS";
    public final static String SMTP_RCPT_ADRRESS_LIST = "SMTP_RCPT_ADRRESS_LIST";
    public final static String SMTP_MESSAGE_SIZE = "SMTP_MESSAGE_SIZE";

    public MailEnvelopeMessage(MailEnvelope env) {
        populate(env);
    }
    
    public void populate(MailEnvelope env) {
        setHeader(SMTP_SENDER_ADRRESS, env.getSender().toString());
        
        StringBuilder rcptBuilder = new StringBuilder();
        
        Iterator<MailAddress> rcpts = env.getRecipients().iterator();
        while(rcpts.hasNext()) {
            String rcpt = rcpts.next().toString();
            rcptBuilder.append(rcpt);
            if (rcpts.hasNext()) {
                rcptBuilder.append(",");
            }
        }
        setHeader(SMTP_RCPT_ADRRESS_LIST, rcptBuilder.toString());
        setHeader(SMTP_MESSAGE_SIZE, env.getSize());
        try {
            setBody(env.getMessageInputStream());
        } catch (Exception e) {
            
        }
    }
}
