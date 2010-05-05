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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.james.protocols.api.AbstractProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.WiringException;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractAuthRequiredToRelayRcptHook;
import org.apache.james.protocols.smtp.core.DataCmdHandler;
import org.apache.james.protocols.smtp.core.DataLineMessageHookHandler;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HeloCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.MailCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.PostmasterAbuseRcptHook;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.RcptCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.SMTPCommandDispatcherLineHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.WelcomeMessageHandler;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
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
        private ProtocolHandlerChain chain;;
        
        public SMTPChannelPipelineFactory() throws WiringException{
            chain = new ProtocolHandlerChainImpl();
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
     * ProtocolChain which handles SMTP command dispatching
     *
     */
    private final class ProtocolHandlerChainImpl extends AbstractProtocolHandlerChain {
        private final List<Object> handlers = new LinkedList<Object>();

        public ProtocolHandlerChainImpl() throws WiringException {
            handlers.add(new SMTPCommandDispatcherLineHandler());
            handlers.add(new ExpnCmdHandler());
            handlers.add(new EhloCmdHandler());
            handlers.add(new HeloCmdHandler());
            handlers.add(new HelpCmdHandler());
            handlers.add(new MailCmdHandler());
            handlers.add(new NoopCmdHandler());
            handlers.add(new QuitCmdHandler());
            handlers.add(new RcptCmdHandler());
            handlers.add(new RsetCmdHandler());
            handlers.add(new VrfyCmdHandler());
            handlers.add(new DataCmdHandler());
            handlers.add(new MailSizeEsmtpExtension());
            handlers.add(new WelcomeMessageHandler());
            handlers.add(new PostmasterAbuseRcptHook());
            handlers.add(new ReceivedDataLineFilter());
            handlers.add(new DataLineMessageHookHandler());
            handlers.add(new ProcessorMessageHook());
            handlers.add(new AllowToRelayHandler());
            wireExtensibleHandlers();
        }

        @Override
        protected List<Object> getHandlers() {
            return handlers;
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
