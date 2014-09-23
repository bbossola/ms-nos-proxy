package com.msnos.proxy.filter.http;

import com.msnos.proxy.filter.AbstractTest;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpRouterTest extends AbstractTest {

    @Before
    public void prepare() throws Exception {
        super.prepare();
    }

    @Test
    public void shouldRemoveServiceFromProxyingWhenFLTReceived() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HTTP_1_1, GET, "http://127.0.0.1:8881/service/path");

        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102");

        HttpRouter router = new HttpRouter(request, microservice);
        simulateMessageFromCloud(new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.FLT, cloud.getIden(), microservice.getAgent().getIden()).with(2).with(UUID.randomUUID()).reliable(false).with(new FltPayload(remote.getAgent().getIden())).make());
        simulateMessageFromCloud(new MessageBuilder(Message.Type.FLT, cloud, microservice.getAgent().getIden()).with(new FltPayload(remote.getAgent().getIden())).with(UUID.randomUUID()).make());

        router.routeRequest(request);
        HttpResponse response = router.handleResponse(validHttpResponse());

        assertFalse(response.headers().contains(SET_COOKIE));
    }

    @Test
    public void shouldSendFinal500IfNoRestApis() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HTTP_1_1, GET, "http://127.0.0.1:8881/service/path");
        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        setupRemoteMicroserviceWithAffinity("some", "other", "10.20.10.102");

        HttpRouter router = new HttpRouter(request, microservice);

        HttpResponse httpResponse = router.handleResponse(new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR));
        assertTrue(getBodyTextFromResponse((DefaultFullHttpResponse) httpResponse).contains("momentarily faulty"));
    }

    @Test
    public void shouldNOTExplodeWhenReceivingABadCookie() throws Exception {
        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("remote", "something", "http://127.0.0.1:8881/remote/something");
        DefaultHttpRequest request = createRequestWithMultipleCookieValuesOneBroken("service", "/path", "remote", "/otherP", getRestApiId(remote));

        HttpRouter router = new HttpRouter(request, microservice);
        router.routeRequest(request);

        HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockResponse.getStatus()).thenReturn(OK);
        when(mockResponse.headers()).thenReturn(new DefaultHttpHeaders());

        int actual = router.handleResponse(mockResponse).getStatus().code();
        assertEquals(200, actual);

    }

    private DefaultHttpRequest createRequestWithMultipleCookieValuesOneBroken(String name, String path, String otherName, String otherPath, long restApiId) {
        DefaultHttpRequest request = httpRequest(name, path);
        Cookie cookieOne = new DefaultCookie(String.format("x-%s%s", name, path), Long.toString(restApiId));
        Cookie cookieTwo = new DefaultCookie(String.format("x-%s%s", otherName, otherPath), "timeToEXPLODE");
        addHeadersToRequest(request, COOKIE, ClientCookieEncoder.encode(cookieOne, cookieTwo));
        return request;
    }

    private long getRestApiId(RemoteMicroservice remote) {
        return getRestApi(remote).getId();
    }

    private RestApi getRestApi(RemoteMicroservice remote) {
        return remote.getApis().iterator().next();
    }

    private DefaultHttpRequest httpRequest(String name, String path) {
        return new DefaultHttpRequest(HTTP_1_1, GET, name + path);
    }

    private void addHeadersToRequest(HttpRequest request, String name, String value) {
        request.headers().add(name, value);
    }

    private String getBodyTextFromResponse(DefaultFullHttpResponse response) {
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < response.content().capacity(); i++) {
            actual.append((char) response.content().getByte(i));
        }
        return actual.toString();
    }

    private Microservice createLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(cloud);
        return ms;
    }
}


