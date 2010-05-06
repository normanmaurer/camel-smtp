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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.james.protocols.api.LineHandler;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.impl.LineHandlerUpstreamHandler;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.stream.ChunkedStream;

/**
 * {@link SMTPSession} implementation for use with Netty
 *
 */
public class SMTPNettySession implements SMTPSession{    
    private static Random random = new Random();

    private boolean relayingAllowed;

    private String smtpID;

    private Map<String, Object> connectionState;

    private SMTPConfiguration theConfigData;

    private int lineHandlerCount = 0;

    protected ChannelHandlerContext handlerContext;
    protected InetSocketAddress socketAddress;
    protected Log logger;
    protected String user;

    
    public SMTPNettySession(SMTPConfiguration theConfigData, Log logger, ChannelHandlerContext handlerContext) {
        this.theConfigData = theConfigData;
        this.logger = logger;
        connectionState = new HashMap<String, Object>();
        smtpID = random.nextInt(1024) + "";
        this.handlerContext = handlerContext;
        this.socketAddress = (InetSocketAddress) handlerContext.getChannel().getRemoteAddress();
        relayingAllowed = theConfigData.isRelayingAllowed(getRemoteIPAddress());    
    }


    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#getRemoteHost()
     */
    public String getRemoteHost() {
        return socketAddress.getHostName();
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#getRemoteIPAddress()
     */
    public String getRemoteIPAddress() {
        return socketAddress.getAddress().getHostAddress();
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#getUser()
     */
    public String getUser() {
        return user;
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#setUser(java.lang.String)
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#isStartTLSSupported()
     */
    public boolean isStartTLSSupported() {
        return false;
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#isTLSStarted()
     */
    public boolean isTLSStarted() {
        return false;
    }

    /**
     * @see org.apache.james.api.protocol.TLSSupportedSession#startTLS()
     */
    public void startTLS() throws IOException {
        // not supported
        
    }

    /**
     * @see org.apache.james.api.protocol.ProtocolSession#getLogger()
     */
    public Log getLogger() {
        return logger;
    }
    

    /*
     * (non-Javadoc)
     * @see org.apache.james.api.protocol.ProtocolSession#writeResponse(org.apache.james.api.protocol.Response)
     */
    public void writeResponse(Response response) {
        Channel channel = handlerContext.getChannel();
        if (response != null && channel.isConnected()) {
            channel.write(response);
            if (response.isEndSession()) {
                channel.close();
            }
        }
    }

    /*
     * 
     */
    public void writeStream(InputStream stream) {
        Channel channel = handlerContext.getChannel();
        if (stream != null && channel.isConnected()) {
            channel.write(new ChunkedStream(stream));
        }
    }
    
    
    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getConnectionState()
     */
    public Map<String, Object> getConnectionState() {
        return connectionState;
    }
    
    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getSessionID()
     */
    public String getSessionID() {
        return smtpID;
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getState()
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getState() {
        Map<String, Object> res = (Map<String, Object>) getConnectionState()
                .get(SMTPSession.SESSION_STATE_MAP);
        if (res == null) {
            res = new HashMap<String, Object>();
            getConnectionState().put(SMTPSession.SESSION_STATE_MAP, res);
        }
        return res;
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#isRelayingAllowed()
     */
    public boolean isRelayingAllowed() {
        return relayingAllowed;
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#resetState()
     */
    public void resetState() {
        // remember the ehlo mode between resets
        Object currentHeloMode = getState().get(CURRENT_HELO_MODE);

        getState().clear();

        // start again with the old helo mode
        if (currentHeloMode != null) {
            getState().put(CURRENT_HELO_MODE, currentHeloMode);
        }
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#popLineHandler()
     */
    public void popLineHandler() {
        handlerContext.getPipeline()
                .remove("lineHandler" + lineHandlerCount);
        lineHandlerCount--;
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#pushLineHandler(org.apache.james.smtpserver.protocol.LineHandler)
     */
    public void pushLineHandler(LineHandler<SMTPSession> overrideCommandHandler) {
        lineHandlerCount++;
        handlerContext.getPipeline().addBefore("coreHandler",
                "lineHandler" + lineHandlerCount,
                new LineHandlerUpstreamHandler<SMTPSession>(overrideCommandHandler));
    }



    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getHelloName()
     */
    public String getHelloName() {
        return theConfigData.getHelloName();
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getMaxMessageSize()
     */
    public long getMaxMessageSize() {
        return theConfigData.getMaxMessageSize();
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getRcptCount()
     */
    @SuppressWarnings("unchecked")
    public int getRcptCount() {
        int count = 0;

        // check if the key exists
        if (getState().get(SMTPSession.RCPT_LIST) != null) {
            count = ((Collection) getState().get(SMTPSession.RCPT_LIST)).size();
        }

        return count;
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getSMTPGreeting()
     */
    public String getSMTPGreeting() {
        return theConfigData.getSMTPGreeting();
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#isAuthSupported()
     */
    public boolean isAuthSupported() {
        return theConfigData.isAuthRequired(socketAddress.getAddress().getHostAddress());
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#setRelayingAllowed(boolean)
     */
    public void setRelayingAllowed(boolean relayingAllowed) {
        this.relayingAllowed = relayingAllowed;
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#sleep(long)
     */
    public void sleep(long ms) {
        //session.getFilterChain().addAfter("connectionFilter", "tarpitFilter",new TarpitFilter(ms));
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#useAddressBracketsEnforcement()
     */
    public boolean useAddressBracketsEnforcement() {
        return theConfigData.useAddressBracketsEnforcement();
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#useHeloEhloEnforcement()
     */
    public boolean useHeloEhloEnforcement() {
        return theConfigData.useHeloEhloEnforcement();
    }

}
