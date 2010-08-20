/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly;

import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.TCPIOClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class SelectorHandlerStateTest extends TestCase {
    public static final int PORT = 17510;

    public void testSelectorHandlerShutdown() throws IOException {
        Controller controller = createController();
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(PORT);
        controller.setSelectorHandler(selectorHandler);

        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            byte[] response = echo(testData);
            
            assertTrue(Arrays.equals(testData, response));
            
            controller.removeSelectorHandler(selectorHandler);
            
            Exception exception = null;
            try {
                response = echo(testData);
            } catch(IOException e) {
                exception = e;
            }
            
            assertNotNull(exception);
            
            final CountDownLatch latch = new CountDownLatch(1);
            controller.addStateListener(new ControllerStateListenerAdapter() {
                @Override
                public void onReady() {
                    latch.countDown();
                }
            });
            
            selectorHandler = new TCPSelectorHandler();
            selectorHandler.setPort(PORT);
            controller.addSelectorHandler(selectorHandler);
            
            try {
                latch.await(5000, TimeUnit.MILLISECONDS);
            } catch(Exception e) {
            }
            
            response = echo(testData);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
        }
        
    }
    
    public void testSelectorHandlerPause() throws IOException {
        Controller controller = createController();
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(PORT);
        controller.setSelectorHandler(selectorHandler);

        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            byte[] response = echo(testData);
            
            assertTrue(Arrays.equals(testData, response));
            
            selectorHandler.pause();
            sleep(2000);
            
            Exception exception = null;
            try {
                response = echo(testData);
            } catch(IOException e) {
                exception = e;
            }
            
            assertNotNull("Exception didn't occur. Response: " + new String(response), exception);
            
            selectorHandler.resume();
            sleep(2000);

            response = echo(testData);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
        }
        
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
        }
    }
    
    private byte[] echo(byte[] packet) throws IOException {
        TCPIOClient client = new TCPIOClient("localhost", PORT);
        byte[] response = null;
        try {
            client.connect();
            client.send(packet);
            response = new byte[packet.length];
            client.receive(response);
            client.close();
        } finally {
            client.close();
        }
        
        return response;
    }

    private Controller createController() {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();

        final Controller controller = new Controller();


        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler() {

                    @Override
                    public ProtocolChain poll() {
                        ProtocolChain protocolChain = protocolChains.poll();
                        if (protocolChain == null) {
                            protocolChain = new DefaultProtocolChain();
                            protocolChain.addFilter(readFilter);
                            protocolChain.addFilter(echoFilter);
                        }
                        return protocolChain;
                    }
                });

        return controller;
    }
    
}
