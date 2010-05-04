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

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.james.protocols.api.ConnectHandler;
import org.apache.james.protocols.api.LineHandler;
import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;

/**
 * {@link ChannelUpstreamHandler} which handles the SMTP Protocol
 *
 */
public class SMTPChannelUpstreamHandler extends SimpleChannelUpstreamHandler implements ChannelAttributeSupport {

    private ProtocolHandlerChain chain;
    private Log logger;
    private SMTPConfiguration config;

    public SMTPChannelUpstreamHandler(ProtocolHandlerChain chain, SMTPConfiguration config, Log logger) {
        this.chain = chain;
        this.logger = logger;
        this.config = config;
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        attributes.set(ctx.getChannel(), new SMTPNettySession(config, logger, ctx));
        super.channelBound(ctx, e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        List<ConnectHandler> connectHandlers = chain.getHandlers(ConnectHandler.class);

        if (connectHandlers != null) {
            for (int i = 0; i < connectHandlers.size(); i++) {
                connectHandlers.get(i).onConnect((ProtocolSession) attributes.get(ctx.getChannel()));
            }
        }
        super.channelConnected(ctx, e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ProtocolSession pSession = (ProtocolSession) attributes.get(ctx.getChannel());
        LinkedList<LineHandler> lineHandlers = chain.getHandlers(LineHandler.class);

        ChannelBuffer buf = (ChannelBuffer) e.getMessage();
        byte[] line = new byte[buf.capacity()];
        buf.getBytes(0, line);

        if (lineHandlers.size() > 0) {

            // Maybe it would be better to use the ByteBuffer here
            ((LineHandler) lineHandlers.getLast()).onLine(pSession, line);
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        cleanup(ctx.getChannel());

        super.channelClosed(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        if (e.getCause() instanceof TooLongFrameException) {
            ctx.getChannel().write(new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Line length exceeded. See RFC 2821 #4.5.3.1."));
        } else {
            if (channel.isConnected()) {
                ctx.getChannel().write(new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unable to process smtp request"));
            }
            cleanup(channel);
            channel.close();
        }
    }

    private void cleanup(Channel channel) {
        SMTPSession session = (SMTPSession) attributes.get(channel);
        if (session != null) {
            session.resetState();
            session = null;
        }
        attributes.remove(channel);

    }

}
