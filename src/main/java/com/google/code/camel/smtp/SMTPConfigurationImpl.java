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

import java.net.URI;
import java.util.Map;

import org.apache.camel.util.URISupport;
import org.apache.james.protocols.smtp.SMTPConfiguration;

public class SMTPConfigurationImpl implements SMTPConfiguration{

    private String bindIP;
    private int bindPort;
    private boolean enforceHeloEhlo = true;
    private boolean enforceBrackets = true;
    private String greeting = "Camel SMTP 0.1";
    private int resetLength = 0;
    private long maxMessageSize = 0;
    private String helloName = "Camel SMTP";

    public void parseURI(URI uri, Map<String, Object> parameters, SMTPComponent component) throws Exception {
        
        bindIP = uri.getHost();
        bindPort = uri.getPort();

     
        Map<String, Object> settings = URISupport.parseParameters(uri);
        if (settings.containsKey("enforceHeloEhlo")) {
            enforceHeloEhlo = Boolean.valueOf((String) settings.get("enforceHeloEhlo"));
        }
        if (settings.containsKey("greeting")) {
            greeting = (String) settings.get("greeting");
        }
        if (settings.containsKey("enforceBrackets")) {
            enforceBrackets = Boolean.valueOf((String) settings.get("enforceBrackets"));
        }
        if (settings.containsKey("maxMessageSize")) {
            maxMessageSize = (Integer.parseInt((String) settings.get("maxMessageSize")));
        }
        if (settings.containsKey("helloName")) {
            helloName = (String) settings.get("helloName");
        }
    }

    public String getHelloName() {
        return helloName;
    }

    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    public int getResetLength() {
        return resetLength;
    }

    public String getSMTPGreeting() {
        return greeting;
    }

    public boolean isAuthRequired(String arg0) {
        return false;
    }

    public boolean isRelayingAllowed(String arg0) {
        return true;
    }

    public boolean isStartTLSSupported() {
        return false;
    }

    public boolean useAddressBracketsEnforcement() {
        return enforceBrackets;
    }

    public boolean useHeloEhloEnforcement() {
        return enforceHeloEhlo;
    }
    
    public String getBindIP() {
        return bindIP;
    }
   
    
    public int getBindPort() {
        return bindPort;
    }
}
