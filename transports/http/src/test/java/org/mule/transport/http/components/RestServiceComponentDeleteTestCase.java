/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.http.components;

import org.mule.module.client.MuleClient;
import org.mule.transport.http.HttpConstants;
import org.mule.transport.http.functional.AbstractMockHttpServerTestCase;
import org.mule.transport.http.functional.MockHttpServer;

import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RestServiceComponentDeleteTestCase extends AbstractMockHttpServerTestCase
{
    private CountDownLatch serverRequestCompleteLatch = new CountDownLatch(1);
    private boolean deleteRequestFound = false;

    @Override
    protected String getConfigResources()
    {
        return "rest-service-component-delete-test.xml";
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 1;
    }

    @Override
    protected MockHttpServer getHttpServer(CountDownLatch serverStartLatch)
    {
        return new SimpleHttpServer(getPorts().get(0), serverStartLatch, serverRequestCompleteLatch);
    }

    public void testRestServiceComponentDelete() throws Exception
    {
        MuleClient client = new MuleClient(muleContext);
        client.send("vm://fromTest", TEST_MESSAGE, null);

        assertTrue(serverRequestCompleteLatch.await(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(deleteRequestFound);
    }

    private class SimpleHttpServer extends MockHttpServer
    {
        public SimpleHttpServer(int listenPort, CountDownLatch startupLatch, CountDownLatch testCompleteLatch)
        {
            super(listenPort, startupLatch, testCompleteLatch);
        }

        @Override
        protected void readHttpRequest(BufferedReader reader) throws Exception
        {
            String requestLine = reader.readLine();
            String httpMethod = new StringTokenizer(requestLine).nextToken();

            deleteRequestFound = httpMethod.equals(HttpConstants.METHOD_DELETE);
        }
    }
}
