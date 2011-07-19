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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.james.protocols.api.AbstractProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.WiringException;
import org.apache.james.protocols.smtp.MailEnvelopeImpl;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPSession;
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
import org.apache.james.protocols.smtp.hook.Hook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.mailet.MailAddress;


/**
 * Handler chain specific to the SMTP Camel chain to provisionally fix an issue with a
 * data handler buffer.
 * 
 * This {@link ProtocolHandlerChain} implementation add all needed handlers to
 * the chain to act as full blown SMTPServer. By default messages will just get
 * rejected after the DATA command.
 * 
 * If you want to accept the messagejust add a {@link MessageHook}
 * implementation to the chain and handle the queuing
 * 
 * 
 * 
 */
public class SMTPProtocolHandlerChain extends AbstractProtocolHandlerChain {
    private final List<Object> defaultHandlers = new ArrayList<Object>();
    private final List<Hook> hooks = new ArrayList<Hook>();
    private final List<Object> handlers = new ArrayList<Object>();

    public SMTPProtocolHandlerChain() throws WiringException {
        defaultHandlers.add(new SMTPCommandDispatcherLineHandler());
        defaultHandlers.add(new ExpnCmdHandler());
        defaultHandlers.add(new EhloCmdHandler());
        defaultHandlers.add(new HeloCmdHandler());
        defaultHandlers.add(new HelpCmdHandler());
        defaultHandlers.add(new MailCmdHandler());
        defaultHandlers.add(new NoopCmdHandler());
        defaultHandlers.add(new QuitCmdHandler());
        defaultHandlers.add(new RcptCmdHandler());
        defaultHandlers.add(new RsetCmdHandler());
        defaultHandlers.add(new VrfyCmdHandler());
        defaultHandlers.add(new DataCmdHandlerFix());  /* Fix for data handler */
        defaultHandlers.add(new MailSizeEsmtpExtension());
        defaultHandlers.add(new WelcomeMessageHandler());
        defaultHandlers.add(new PostmasterAbuseRcptHook());
        defaultHandlers.add(new ReceivedDataLineFilter());
        defaultHandlers.add(new DataLineMessageHookHandler());
        copy();
        wireExtensibleHandlers();
    }

    /**
     * Add the hook to the chain
     * 
     * @param hook
     * @throws WiringException
     */
    public final synchronized void addHook(Hook hook) throws WiringException {
        addHook(hooks.size(), hook);
    }

    /**
     * Add the hook to the chain on the given index
     * 
     * @param index
     * @param hook
     * @throws WiringException
     */
    public final synchronized void addHook(int index, Hook hook) throws WiringException {
        hooks.add(index, hook);
        copy();
        wireExtensibleHandlers();

    }

    /**
     * Remove the Hook found on the given index from the chain
     * 
     * @param index
     * @return hook
     * @throws WiringException
     */
    public final synchronized Hook removeHook(int index) throws WiringException {
        Hook hook = hooks.remove(index);
        handlers.remove(hook);
        wireExtensibleHandlers();
        return hook;

    }

    /**
     * Return the index of the given hook
     * 
     * @param hook
     * @return index
     */
    public synchronized int getIndexOfHook(Hook hook) {
        return hooks.indexOf(hook);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.protocols.api.AbstractProtocolHandlerChain#getHandlers()
     */
    @Override
    protected synchronized List<Object> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    /**
     * Copy the lists
     */
    private void copy() {
        handlers.clear();
        handlers.addAll(defaultHandlers);
        handlers.addAll(hooks);
    }
    
    private static class DataCmdHandlerFix extends DataCmdHandler
    {
    	@Override
        protected SMTPResponse doDATA(SMTPSession session, String argument) 
    	{
    		SMTPResponse retVal = super.doDATA(session, argument);
    		
    		MailEnvelopeImplFix env = new MailEnvelopeImplFix();
    		MailEnvelopeImpl existingEnv = (MailEnvelopeImpl)session.getState().remove(MAILENV);
    		env.setRecipients(existingEnv.getRecipients());
    		env.setSender(existingEnv.getSender());
            session.getState().put(MAILENV, env);
            
            return retVal;
        }
    }
    
    private static class MailEnvelopeImplFix extends MailEnvelopeImpl
    {
    	   private List<MailAddress> recipients;

    	    private MailAddress sender;

    	    private ByteArrayOutputStream outputStream;

    	    /*
    	     * (non-Javadoc)
    	     * @see org.apache.james.smtpserver.protocol.MailEnvelope#getSize()
    	     */
    	    @Override
    	    public int getSize() 
    	    {
    	        if (outputStream == null)
    	            return -1;
    	        return outputStream.size();
    	    }

    	    /*
    	     * (non-Javadoc)
    	     * @see org.apache.james.smtpserver.protocol.MailEnvelope#getRecipients()
    	     */
    	    @Override
    	    public List<MailAddress> getRecipients() 
    	    {
    	        return recipients;
    	    }

    	    /*
    	     * (non-Javadoc)
    	     * @see org.apache.james.smtpserver.protocol.MailEnvelope#getSender()
    	     */
    	    @Override
    	    public MailAddress getSender() {
    	        return sender;
    	    }

    	    /**
    	     * Set the recipients of the mail
    	     * 
    	     * @param recipientCollection
    	     */
    	    @Override
    	    public void setRecipients(List<MailAddress> recipientCollection) {
    	        this.recipients = recipientCollection;
    	    }

    	    /**
    	     * Set the sender of the mail
    	     * 
    	     * @param sender
    	     */
    	    @Override
    	    public void setSender(MailAddress sender) {
    	        this.sender = sender;
    	    }

    	    /*
    	     * (non-Javadoc)
    	     * @see org.apache.james.smtpserver.protocol.MailEnvelope#getMessageOutputStream()
    	     */
    	    @Override
    	    public OutputStream getMessageOutputStream() 
    	    {
    	        if (outputStream == null)    	        	
    	        	this.outputStream = new ByteArrayOutputStream(100000);
    	        
    	        return outputStream;
    	    }

    	    /*
    	     * (non-Javadoc)
    	     * @see org.apache.james.smtpserver.protocol.MailEnvelope#getMessageInputStream()
    	     */
    	    @Override
    	    public InputStream getMessageInputStream() {
    	        return new ByteArrayInputStream(outputStream.toByteArray());
    	    }
    }
}
