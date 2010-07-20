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

import org.apache.commons.logging.Log;
import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.impl.AbstractChannelUpstreamHandler;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;

/**
 * {@link ChannelUpstreamHandler} which handles the SMTP Protocol
 *
 */
public class SMTPChannelUpstreamHandler extends AbstractChannelUpstreamHandler {
    private Log logger;
    private SMTPConfiguration config;

    public SMTPChannelUpstreamHandler(ProtocolHandlerChain chain, SMTPConfiguration config, Log logger) {
    	super(chain);
    	this.logger = logger;
    	this.config = config;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        if (e.getCause() instanceof TooLongFrameException) {
            ctx.getChannel().write(new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Line length exceeded. See RFC 2821 #4.5.3.1."));
        } else {
            if (channel.isConnected()) {
                e.getCause().printStackTrace();
            }
            cleanup(channel);
            channel.close();
        }
    }

    @Override
    protected ProtocolSession createSession(ChannelHandlerContext ctx) throws Exception {
        return new SMTPNettySession(config, logger, ctx);
    }

}
