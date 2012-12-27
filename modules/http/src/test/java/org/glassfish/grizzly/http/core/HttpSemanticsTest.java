/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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
 * file and include the License file at packager/legal/LICENSE.txt.
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

package org.glassfish.grizzly.http.core;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket.Builder;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;


/**
 * Test cases to validate HTTP protocol semantics.
 */
public class HttpSemanticsTest extends TestCase {

    public static final int PORT = 19004;
    private static final int MAX_HEADERS_SIZE = 8192;
    
    private HttpServerFilter httpServerFilter =
            new HttpServerFilter(false, MAX_HEADERS_SIZE, new KeepAlive(), null);

    // ------------------------------------------------------------ Test Methods


    public void testUnsupportedProtocol() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .header("Host", "localhost:" + PORT)
                .uri("/path")
                .protocol("HTTP/1.2")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(505);
        result.addHeader("Connection", "close");
        result.setStatusMessage("http version not supported");
        doTest(request, result);
 
    }


    public void testHttp11NoHostHeader() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol(Protocol.HTTP_1_1)
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(400);
        result.addHeader("Connection", "close");
        result.setStatusMessage("bad request");
        doTest(request, result);

    }


    public void testHttp09ConnectionCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/0.9")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);
        
    }


    public void testHttp10ConnectionCloseNoConnectionRequestHeaderTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/1.0")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);
        
    }


    public void testHttp11RequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/1.1")
                .header("Host", "localhost:" + PORT)
                .header("Connection", "close")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);

    }

    public void testHttp11NoExplicitRequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);

    }

    public void testHttp10NoContentLength() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.0")
                .build();
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        result.appendContent("Content");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                content.setLast(true);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }

    public void testHeadFixedLength() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("HEAD")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.0")
                .build();
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.addHeader("Content-Length", "7");
        result.addHeader("!Transfer-Encoding", "");
        result.setStatusMessage("ok");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                response.setContentLength(content.getContent().remaining());
                content.setLast(true);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }

    public void testHeadChunked() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("HEAD")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.0")
                .chunked(true)
                .build();
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.addHeader("Transfer-Encoding", "chunked");
        result.addHeader("!Content-Length", "");

        result.setStatusMessage("ok");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                response.setChunked(true);
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                content.setLast(true);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }


    public void testHttp11GetNoContentLengthNoChunking() throws Throwable {

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.addHeader("!Transfer-Encoding", "chunked");
        result.setStatusMessage("ok");
        result.appendContent("Content");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                // Setting last flag to false to make sure HttpServerFilter will not
                // add content-length header
                content.setLast(false);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }

    public void testHttp11PostNoContentLengthNoChunking() throws Throwable {

        final HttpRequestPacket header = HttpRequestPacket.builder()
                .method("POST")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        final HttpContent chunk1 = HttpContent.builder(header)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "0\r\nHello"))
                .build();
        final HttpContent chunk2 = HttpContent.builder(header)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "0\r\nWorld"))
                .build();
        
        List<HttpContent> request = Arrays.asList(chunk1, chunk2);
        
        final String testMsg = "0\r\nHello0\r\nWorld";
        
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.addHeader("!Transfer-Encoding", "chunked");
        result.setStatusMessage("ok");
        result.appendContent(testMsg);
        doTest(new ClientFilter(request, result, 1000), new BaseFilter() {
            int counter = 0;
            
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent requestContent = ctx.getMessage();
                HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                
                final Buffer payload = requestContent.getContent().duplicate();
                counter += payload.remaining();
                
                HttpContent responseContent = response.httpContentBuilder().
                        content(payload).build();
                // Setting last flag to false to make sure HttpServerFilter will not
                // add content-length header
                ctx.write(responseContent);

                if (counter == testMsg.length()) {
                    ctx.flush(new FlushAndCloseHandler());
                }
                
                return ctx.getStopAction();
            }
        });
    }

    public void testUpgradeIgnoresTransferEncoding() throws Throwable {

        final HttpRequestPacket header = HttpRequestPacket.builder()
                .method("POST")
                .uri("/path")
                .contentLength(1)
                .header(Header.TransferEncoding, "chunked")
                .header(Header.Server, "localhost:" + PORT)
                .header(Header.Upgrade, "test")
                .protocol("HTTP/1.1")
                .build();

        final HttpContent chunk1 = HttpContent.builder(header)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "0\r\nHello"))
                .build();
        final HttpContent chunk2 = HttpContent.builder(header)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "0\r\nWorld"))
                .build();
        
        List<HttpContent> request = Arrays.asList(chunk1, chunk2);
        
        final String testMsg = "0\r\nHello0\r\nWorld";
        
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(101);
        result.setStatusMessage("Switching Protocols");
        result.addHeader("Connection", "Upgrade");
        result.addHeader("Upgrade", "test");
        result.addHeader("!Transfer-Encoding", "");
        result.addHeader("!ContentLength", "");
        result.appendContent(testMsg);
        doTest(new ClientFilter(request, result, 1000), new BaseFilter() {
            int packetCounter = 0;
            int contentCounter = 0;
            
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent requestContent = ctx.getMessage();
                HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                
                final Buffer payload = requestContent.getContent().duplicate();
                contentCounter += payload.remaining();
                if (packetCounter++ == 0) {
                    response.setStatus(HttpStatus.SWITCHING_PROTOCOLS_101);
                    response.setHeader(Header.Connection, "Upgrade");
                    response.setHeader(Header.Upgrade, request.getHeader(Header.Upgrade));
                }
                
                HttpContent responseContent = response.httpContentBuilder().
                        content(payload).build();
                // Setting last flag to false to make sure HttpServerFilter will not
                // add content-length header
                ctx.write(responseContent);

                if (contentCounter == testMsg.length()) {
                    ctx.flush(new FlushAndCloseHandler());
                }
                
                return ctx.getStopAction();
            }
        });
    }
    
    public void testHttp1AutoContentLengthOnSingleChunk() throws Throwable {

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!Transfer-Encoding", "chunked");
        result.addHeader("Content-Length", "7");
        result.setStatusMessage("ok");
        result.appendContent("Content");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                // HttpServerFilter should apply content-length implicitly
                content.setLast(true);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }
    
    public void testContentLengthDuplicationSame() throws Throwable {

        String requestString = "POST /path HTTP/1.1\r\n"
                + "Host: localhost:" + PORT + "\r\n"
                + "Connection: close\r\n"
                + "Content-Length: 10\r\n"
                + "Content-Length: 10\r\n"
                + "\r\n"
                + "0123456789";

        Buffer request = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, requestString);
        
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);

    }
    
    public void testContentLengthDuplicationDifferent() throws Throwable {

        String requestString = "POST /path HTTP/1.1\r\n"
                + "Host: localhost:" + PORT + "\r\n"
                + "Connection: close\r\n"
                + "Content-Length: 10\r\n"
                + "Content-Length: 20\r\n"
                + "\r\n"
                + "0123456789";

        Buffer request = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, requestString);
        
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(400);
        result.addHeader("Connection", "close");
        result.setStatusMessage("bad request");
        doTest(request, result);

    }
    
    public void testDefaultContentTypeWithProvidedCharset() throws Throwable {
        final BaseFilter serverResponseFilter = new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();

                if (!httpContent.isLast()) {
                    return ctx.getStopAction(httpContent);
                }

                HttpRequestPacket request =
                        (HttpRequestPacket) httpContent.getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                response.setCharacterEncoding("Big5");
                //                response.setContentType("text/html;charset=\"Big5\"");
                response.setContentLength(0);
                ctx.write(response);
                return ctx.getStopAction();
            }
        };

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!Transfer-Encoding", "chunked");
        result.addHeader("Content-Length", "0");
        result.setStatusMessage("ok");

        result.addHeader("!Content-Type", "text/html;charset=Big5");
        httpServerFilter.setDefaultResponseContentType(null);
        doTest(createHttpRequest(), result, serverResponseFilter);
        
        result.addHeader("Content-Type", "text/html;charset=Big5");
        httpServerFilter.setDefaultResponseContentType("text/html");
        doTest(createHttpRequest(), result, serverResponseFilter);

        result.addHeader("Content-Type", "text/html;charset=Big5");
        httpServerFilter.setDefaultResponseContentType("text/html; charset=iso-8859-1");
        doTest(createHttpRequest(), result, serverResponseFilter);
        
        result.addHeader("Content-Type", "text/html;a=b;c=d;charset=Big5");
        httpServerFilter.setDefaultResponseContentType("text/html; charset=iso-8859-1;a=b;c=d");
        doTest(createHttpRequest(), result, serverResponseFilter);
        
        result.addHeader("Content-Type", "text/html;a=b;c=d;charset=Big5");
        httpServerFilter.setDefaultResponseContentType("text/html;a=b;charset=iso-8859-1;c=d");
        doTest(createHttpRequest(), result, serverResponseFilter);
        
        result.addHeader("Content-Type", "text/html;a=b;c=d;charset=Big5");
        httpServerFilter.setDefaultResponseContentType("text/html;a=b;c=d;charset=iso-8859-1");
        doTest(createHttpRequest(), result, serverResponseFilter);
    }
        
    public void testHttpHeadersLimit() throws Throwable {
        final Builder builder = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .maxNumHeaders(-1);
        

        int i = 1;
        int headerSize = MAX_HEADERS_SIZE;
        while (headerSize >= 0) {
            final String name = "Header-" + i;
            final String value = "Value-" + i;
            
            builder.header(name, value);
            i++;
            headerSize -= (name.length() + value.length());
        }
        
        final HttpRequestPacket request = builder.build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(400);
        result.addHeader("!Transfer-Encoding", null);
        result.addHeader("Content-Length", "0");
        result.setStatusMessage("Bad request");
        doTest(request, result);
    }

    // --------------------------------------------------------- Private Methods

    
    private HttpRequestPacket createHttpRequest() {
        return HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();
    }
    
    private void doTest(Object request, ExpectedResult expectedResults, Filter serverFilter)
            throws Throwable {
        final ClientFilter clientFilter = new ClientFilter(request,
                expectedResults);

        doTest(clientFilter, serverFilter);
    }

    private void doTest(final ClientFilter clientFilter, Filter serverFilter)
            throws Throwable {
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(1024));
        filterChainBuilder.add(httpServerFilter);
        filterChainBuilder.add(serverFilter);
        FilterChain filterChain = filterChainBuilder.build();

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(filterChain);
        TCPNIOTransport ctransport = TCPNIOTransportBuilder.newInstance().build();
        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(1024));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(clientFilter);
            ctransport.setFilterChain(clientFilterChainBuilder.build());

            ctransport.start();

            Future<Connection> connectFuture = ctransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                clientFilter.getFuture().get(30, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } finally {
            transport.stop();
            ctransport.stop();
        }
    }

    private void doTest(Object request, ExpectedResult expectedResults)
    throws Throwable {

        doTest(request, expectedResults, new SimpleResponseFilter());
        
    }


    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private Object request;
        private ExpectedResult expectedResult;
        private boolean validated;
        private HttpContent currentContent;
        StringBuilder accumulatedContent = new StringBuilder();
        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();

        final long delayMillis;
        // -------------------------------------------------------- Constructors


        public ClientFilter(Object request,
                            ExpectedResult expectedResults) {
            this(request, expectedResults, 0);
        }

        public ClientFilter(Object request,
                            ExpectedResult expectedResults,
                            long delayMillis) {

            this.request = request;
            this.expectedResult = expectedResults;
            this.delayMillis = delayMillis;

        }

        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
              throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Connected... Sending the request: {0}", request);
            }

            if (request instanceof List) {
                for (final Object chunk : (List) request) {
                    ctx.write(chunk);
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException ignored) {
                    }
                }
            } else {
                ctx.write(request);
            }

            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {

            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");
            if (httpContent.isLast()) {
                accumulatedContent.append(httpContent.getContent().toStringContent(Charsets.UTF8_CHARSET));
                validate(httpContent, accumulatedContent.toString());
            } else {
                accumulatedContent.append(httpContent.getContent().toStringContent(Charsets.UTF8_CHARSET));
                currentContent = httpContent;
            }

            return ctx.getStopAction();
        }

        private void validate(HttpContent httpContent, String content) {
            if (validated) {
                return;
            }
            validated = true;
            try {
                HttpResponsePacket response =
                        (HttpResponsePacket) httpContent.getHttpHeader();
                if (expectedResult.getStatusCode() != -1) {
                    assertEquals(expectedResult.getStatusCode(),
                                 response.getStatus());
                }
                if (expectedResult.getProtocol() != null) {
                    assertEquals(expectedResult.getProtocol(),
                                 response.getProtocol().getProtocolString());
                }
                if (expectedResult.getStatusMessage() != null) {
                    assertEquals(expectedResult.getStatusMessage().toLowerCase(),
                                 response.getReasonPhrase().toLowerCase());
                }

                assertEquals(expectedResult.getContent(), content);

                if (!expectedResult.getExpectedHeaders().isEmpty()) {
                    for (Map.Entry<String,String> entry : expectedResult.getExpectedHeaders().entrySet()) {
                        if (entry.getKey().charAt(0) != '!') {
                            assertTrue("Missing header: " + entry.getKey(),
                                       response.containsHeader(entry.getKey()));
                            assertEquals(entry.getValue().toLowerCase(),
                                         response.getHeader(entry.getKey()).toLowerCase());
                        } else {
                            final String headerName = entry.getKey().substring(1);
                            assertFalse("Header should not be present: " + headerName +
                                    " but header exists w/ value=" + response.getHeader(headerName),
                                       response.containsHeader(headerName));
                        }
                    }
                }
                testResult.result(Boolean.TRUE);
            } catch (Throwable t) {
                testResult.failure(t);
            }
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
              throws IOException {
            validate(currentContent, accumulatedContent.toString());
            return ctx.getStopAction();
        }

        public Future<Boolean> getFuture() {
            return testResult;
        }
    }


    private static final class SimpleResponseFilter extends BaseFilter {
        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = ctx.getMessage();

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }
            
            HttpRequestPacket request =
                    (HttpRequestPacket) httpContent.getHttpHeader();
            HttpResponsePacket response = request.getResponse();
            HttpStatus.OK_200.setValues(response);
            response.setContentLength(0);
            ctx.write(response);
            return ctx.getStopAction();
        }
    }


    private static final class ExpectedResult {

        private int statusCode = -1;
        private Map<String,String> expectedHeaders =
                new HashMap<String,String>();
        private String protocol;
        private String statusMessage;
        private StringBuilder builder = new StringBuilder();

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public void addHeader(String name, String value) {
            if (name.startsWith("!")) {
                expectedHeaders.remove(name.substring(1));
            } else {
                expectedHeaders.remove("!" + name);
            }
            expectedHeaders.put(name, value);
        }

        public Map<String, String> getExpectedHeaders() {
            return Collections.unmodifiableMap(expectedHeaders);
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public void setStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
        }

        public void appendContent(String content) {
            builder.append(content);
        }

        public String getContent() {
            return builder.toString();
        }
    }

    private static class FlushAndCloseHandler extends EmptyCompletionHandler {
        @Override
        public void completed(Object result) {
            final WriteResult wr = (WriteResult) result;
            try {
                wr.getConnection().closeSilently();
            } finally {
                wr.recycle();
            }
        }
    }
}
