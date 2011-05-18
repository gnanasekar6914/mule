/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing.outbound;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.processor.MessageRouter;
import org.mule.api.registry.RegistrationException;
import org.mule.api.routing.CouldNotRouteOutboundMessageException;
import org.mule.api.routing.RoutingException;
import org.mule.config.i18n.MessageFactory;
import org.mule.routing.CorrelationMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>AbstractRecipientList</code> is used to dispatch a single event to
 * multiple recipients over the same transport. The recipient targets can be
 * configured statically or can be obtained from the message payload.
 */

public abstract class AbstractRecipientList extends FilteringOutboundRouter implements MessageRouter
{
    /**
     * logger used by this class
     */
    protected final Log logger = LogFactory.getLog(getClass());

    private final ConcurrentMap recipientCache = new ConcurrentHashMap();

    private Boolean synchronous;

    @Override
    public MuleEvent route(MuleEvent event)
        throws RoutingException
    {
        MuleMessage message = event.getMessage();

        List recipients = this.getRecipients(event);
        List<MuleEvent> results = new ArrayList<MuleEvent>();
        
        if (enableCorrelation != CorrelationMode.NEVER)
        {
            boolean correlationSet = message.getCorrelationGroupSize() != -1;
            if (correlationSet && (enableCorrelation == CorrelationMode.IF_NOT_SET))
            {
                logger.debug("CorrelationId is already set, not setting Correlation group size");
            }
            else
            {
                // the correlationId will be set by the AbstractOutboundRouter
                message.setCorrelationGroupSize(recipients.size());
            }
        }

        OutboundEndpoint endpoint = null;
        MuleMessage request;

        for (Iterator iterator = recipients.iterator(); iterator.hasNext();)
        {
            Object recipient = iterator.next();
            // Make a copy of the message. Question is do we do a proper clone? in
            // which case there
            // would potentially be multiple messages with the same id...
            request = new DefaultMuleMessage(message.getPayload(), message, muleContext);
            try
            {
                endpoint = getRecipientEndpoint(request, recipient);
    
                boolean sync = 
                    (this.synchronous == null ? endpoint.getExchangePattern().hasResponse() : this.synchronous.booleanValue());
                
                if (sync)
                {
                    results.add(sendRequest(event, request, endpoint, true));
                }
                else
                {
                    sendRequest(event, request, endpoint, false);
                }
            }
            catch (MuleException e)
            {
                throw new CouldNotRouteOutboundMessageException(event, endpoint, e);
            }
        }

        return resultsHandler.aggregateResults(results, event, muleContext);
    }

    protected OutboundEndpoint getRecipientEndpoint(MuleMessage message, Object recipient) throws MuleException
    {
        OutboundEndpoint endpoint = null;
        if (recipient instanceof OutboundEndpoint)
        {
            endpoint = (OutboundEndpoint) recipient;
        }
        else if (recipient instanceof EndpointURI)
        {
            endpoint = getRecipientEndpointFromUri((EndpointURI) recipient);
        }
        else if (recipient instanceof String)
        {
            endpoint = getRecipientEndpointFromString(message, (String) recipient);
        }
        if (null == endpoint)
        {
            throw new RegistrationException(MessageFactory.createStaticMessage("Failed to create endpoint for: " + recipient));
        }

        OutboundEndpoint existingEndpoint = (OutboundEndpoint) recipientCache.putIfAbsent(recipient, endpoint);
        if (existingEndpoint != null)
        {
            endpoint = existingEndpoint;
        }
        return endpoint;
    }

    protected OutboundEndpoint getRecipientEndpointFromUri(EndpointURI uri)
            throws MuleException
    {
        OutboundEndpoint endpoint = null;
        if (null != getMuleContext() && null != getMuleContext().getRegistry())
        {
            endpoint = getMuleContext().getEndpointFactory().getOutboundEndpoint(uri.getAddress());
        }
        if (null != endpoint)
        {
            muleContext.getRegistry().applyLifecycle(endpoint);
        }
        return endpoint;
    }

    protected OutboundEndpoint getRecipientEndpointFromString(MuleMessage message, String recipient)
            throws MuleException
    {
        OutboundEndpoint endpoint = (OutboundEndpoint) recipientCache.get(recipient);
        if (null == endpoint && null != getMuleContext() && null != getMuleContext().getRegistry())
        {
            endpoint = getMuleContext().getEndpointFactory().getOutboundEndpoint(recipient);
        }
        return endpoint;
    }

    public Boolean getSynchronous()
    {
        return synchronous;
    }

    public void setSynchronous(Boolean synchronous)
    {
        this.synchronous = synchronous;
    }

    @Override
    public boolean isDynamicRoutes()
    {
        return true;
    }

    protected abstract List getRecipients(MuleEvent event) throws CouldNotRouteOutboundMessageException;

}
