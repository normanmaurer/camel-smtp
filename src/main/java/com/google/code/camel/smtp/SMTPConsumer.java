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

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.james.protocols.api.WiringException;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractAuthRequiredToRelayRcptHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

/**
 * Consumer which starts an SMTPServer and forward mails to the processer once they are received
 * 
 *
 */
public class SMTPConsumer extends DefaultConsumer {

    private ServerBootstrap bootstrap;
    private SMTPURIConfiguration config;
    private Log logger = LogFactory.getLog(SMTPConsumer.class);
    
    public SMTPConsumer(Endpoint endpoint, Processor processor, SMTPURIConfiguration config) {
        super(endpoint, processor);
        this.config = config;
    }

    /**
     * Startup the SMTP Server
     */
    @Override
    protected void doStart() throws Exception {
        super.doStart();

        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        // Configure the pipeline factory.
        bootstrap.setPipelineFactory(new SMTPChannelPipelineFactory());

        // Bind and start to accept incoming connections.
        bootstrap.setOption("backlog", 250);
        bootstrap.setOption("reuseAddress", true);
        bootstrap.bind(new InetSocketAddress(config.getBindIP(), config.getBindPort()));
    }

    /**
     * Shutdown the SMTPServer
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        bootstrap.releaseExternalResources();
    }

    
    private final class SMTPChannelPipelineFactory implements ChannelPipelineFactory {
        private SMTPProtocolHandlerChain chain;
        
        public SMTPChannelPipelineFactory() throws WiringException{
            chain = new SMTPProtocolHandlerChain();
            chain.addHook(new AllowToRelayHandler());
            chain.addHook(new ProcessorMessageHook());
        }
        
        public ChannelPipeline getPipeline() throws Exception {
            
            // Create a default pipeline implementation.
            ChannelPipeline pipeline = pipeline();

            // Add the text line decoder which limit the max line length, don't
            // strip the delimiter and use CRLF as delimiter
            pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, false, Delimiters.lineDelimiter()));

            // encoder
            pipeline.addLast("encoderResponse", new SMTPResponseEncoder());

            pipeline.addLast("streamer", new ChunkedWriteHandler());
            pipeline.addLast("coreHandler", new SMTPChannelUpstreamHandler(chain, config, logger));

            return pipeline;
        }

    }

 
    /**
     * Check if the domain is local and if so accept the email. If not reject it
     * 
     *
     */
    private final class AllowToRelayHandler extends AbstractAuthRequiredToRelayRcptHook {

        @Override
        protected boolean isLocalDomain(String domain) {
            List<String> domains = config.getLocalDomains();
            if (domains == null) {
                // no restriction was set.. accept it!
                return true;
            } else {
                return domains.contains(domain.trim());
            }
        }
        
    }
    /**
     * Send the {@link Exchange} to the {@link Processor} after receiving a message via SMTP
     *
     */
    private final class ProcessorMessageHook implements MessageHook {

        /*
         * (non-Javadoc)
         * @see org.apache.james.protocols.smtp.hook.MessageHook#onMessage(org.apache.james.protocols.smtp.SMTPSession, org.apache.james.protocols.smtp.MailEnvelope)
         */
        public HookResult onMessage(SMTPSession arg0, MailEnvelope env) {
            Exchange exchange = getEndpoint().createExchange();
            exchange.setIn(new MailEnvelopeMessage(env));
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }
            return new HookResult(HookReturnCode.OK);
        }
        
    }

}
