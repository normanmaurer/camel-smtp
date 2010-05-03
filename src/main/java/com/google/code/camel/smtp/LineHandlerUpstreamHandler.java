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

import org.apache.james.protocols.api.LineHandler;
import org.apache.james.protocols.api.ProtocolSession;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * {@link ChannelUpstreamHandler} implementation which will call a given {@link LineHandler} implementation
 *
 * @param <Session>
 */
@ChannelPipelineCoverage("all")
public class LineHandlerUpstreamHandler<Session extends ProtocolSession> extends SimpleChannelUpstreamHandler implements ChannelAttributeSupport{

    private LineHandler<Session> handler;
    
    public LineHandlerUpstreamHandler(LineHandler<Session> handler) {
        this.handler = handler;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Session pSession = (Session) attributes.get(ctx.getChannel());
        
        ChannelBuffer buf = (ChannelBuffer) e.getMessage();      

        // copy the ChannelBuffer to a byte array to process the LineHandler
        byte[] line = new byte[buf.capacity()];
        buf.getBytes(0, line);

        handler.onLine(pSession, line);

        
    }

}
