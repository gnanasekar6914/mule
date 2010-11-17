/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.launcher;

import org.mule.module.launcher.application.Application;
import org.mule.module.reboot.MuleContainerBootstrapUtils;
import org.mule.util.FileUtils;
import org.mule.util.FilenameUtils;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DefaultMuleDeployer implements MuleDeployer
{

    protected transient final Log logger = LogFactory.getLog(getClass());
    protected DeploymentService deploymentService;


    public DefaultMuleDeployer(DeploymentService deploymentService)
    {
        this.deploymentService = deploymentService;
    }

    public void deploy(Application app)
    {
        final ReentrantLock lock = deploymentService.getLock();
        try
        {
            if (!lock.tryLock(0, TimeUnit.SECONDS))
            {
                return;
            }
            app.install();
            app.init();
            app.start();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return;
        }
        catch (Throwable t)
        {
            // TODO logging
            t.printStackTrace();
        }
        finally
        {
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }
    }

    public void undeploy(Application app)
    {
        final ReentrantLock lock = deploymentService.getLock();
        try
        {
            if (!lock.tryLock(0, TimeUnit.SECONDS))
            {
                return;
            }

            app.stop();
            app.dispose();
            final File appDir = new File(MuleContainerBootstrapUtils.getMuleAppsDir(), app.getAppName());
            FileUtils.deleteDirectory(appDir);
            // remove a marker, harmless, but a tidy app dir is always better :)
            File marker = new File(MuleContainerBootstrapUtils.getMuleAppsDir(), String.format("%s-anchor.txt", app.getAppName()));
            marker.delete();
            Introspector.flushCaches();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return;
        }
        catch (Throwable t)
        {
            // TODO logging
            t.printStackTrace();
        }
        finally
        {
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }
    }

    public Application installFromAppDir(String packedMuleAppFileName) throws IOException
    {
        final ReentrantLock lock = deploymentService.getLock();
        try
        {
            if (!lock.tryLock(0, TimeUnit.SECONDS))
            {
                throw new IOException("Another deployment operation is in progress");
            }

            final File appsDir = MuleContainerBootstrapUtils.getMuleAppsDir();
            File appFile = new File(appsDir, packedMuleAppFileName);
            // basic security measure: outside apps dir use installFrom(url) and go through any
            // restrictions applied to it
            if (!appFile.getParentFile().equals(appsDir))
            {
                throw new SecurityException("installFromAppDir() can only deploy from $MULE_HOME/apps. Use installFrom(url) instead.");
            }
            return installFrom(appFile.toURL());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Install operation has been interrupted");
        }
        finally
        {
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }
    }

    public Application installFrom(URL url) throws IOException
    {
        // TODO plug in app-bloodhound/validator here?
        if (!url.toString().endsWith(".zip"))
        {
            throw new IllegalArgumentException("Only Mule application zips are supported: " + url);
        }

        final ReentrantLock lock = deploymentService.getLock();

        String appName;
        try
        {
            if (!lock.tryLock(0, TimeUnit.SECONDS))
            {
                throw new IOException("Another deployment operation is in progress");
            }

            final File appsDir = MuleContainerBootstrapUtils.getMuleAppsDir();

            final String fullPath = url.toURI().toString();

            if (logger.isInfoEnabled())
            {
                logger.info("Exploding a Mule application archive: " + fullPath);
            }

            appName = FilenameUtils.getBaseName(fullPath);
            File appDir = new File(appsDir, appName);
            // normalize the full path + protocol to make unzip happy
            final File source = new File(url.toURI());
            
            FileUtils.unzip(source, appDir);
            if ("file".equals(url.getProtocol()))
            {
                FileUtils.deleteQuietly(source);
            }
        }
        catch (URISyntaxException e)
        {
            final IOException ex = new IOException(e.getMessage());
            ex.fillInStackTrace();
            throw ex;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Install operation has been interrupted");
        }
        finally
        {
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }

        // appname is never null by now
        return deploymentService.getAppFactory().createApp(appName);
    }
}
