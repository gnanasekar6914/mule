/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.http.functional;

import org.mule.tck.DynamicPortTestCase;
import org.mule.tck.functional.CounterCallback;
import org.mule.tck.functional.FunctionalTestComponent;

import java.util.concurrent.atomic.AtomicInteger;

public class PollingEtagTestCase extends DynamicPortTestCase
{
    private static final int WAIT_TIME = 2500;

    protected String getConfigResources()
    {
        return "polling-etag-test.xml";
    }

    public void testPollingReceiversRestart() throws Exception
    {
        Object ftc = getComponent("Test");
        assertTrue("FunctionalTestComponent expected", ftc instanceof FunctionalTestComponent);

        AtomicInteger pollCounter = new AtomicInteger(0);
        ((FunctionalTestComponent) ftc).setEventCallback(new CounterCallback(pollCounter));

        // should be enough to poll for multiple messages
        Thread.sleep(WAIT_TIME);

        assertEquals(1, pollCounter.get());
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 1;
    }
}

