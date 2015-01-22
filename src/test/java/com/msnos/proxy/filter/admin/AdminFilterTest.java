package com.msnos.proxy.filter.admin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.msnos.proxy.filter.AbstractTest;
import com.workshare.msnos.usvc.Microservice;

public class AdminFilterTest extends AbstractTest {

    private Gson gson;

    @Before
    public void prepare() throws Exception {
        super.prepare();
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Test
    public void shouldReturn404WhenUnexistingUrlIsCalled() throws Exception {
        Microservice microservice = mock(Microservice.class);

        DefaultFullHttpResponse response = invoke(microservice, "unexistent-endpoint");

        assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());
    }

    @Test
    public void shouldReturnPongWhenAdminPingURI() throws Exception {
        Microservice microservice = mock(Microservice.class);

        DefaultFullHttpResponse response = invoke(microservice, "ping");

        assertJsonReturned(response, "pong", "text/plain; charset=UTF-8");
    }

    @Test
    public void shouldReturnListWhenAdminRoutesInURI() throws Exception {
        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102/25");
        setupRemoteMicroserviceWithAffinity("other", "diff", "82.20.230.102/25");

        DefaultFullHttpResponse response = invoke(microservice, "routes");

        String expected = gson.toJson(microcloud.getApis());
        assertJsonReturned(response, expected, "application/json; charset=UTF-8");
    }

    @Test
    public void shouldReturnListWhenAdminMicroservicesInURI() throws Exception {
        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102/25");
        setupRemoteMicroserviceWithAffinity("other", "diff", "82.20.230.102/25");

        DefaultFullHttpResponse response = invoke(microservice, "microservices");

        String expected = gson.toJson(microcloud.getMicroServices());
        assertJsonReturned(response, expected, "application/json; charset=UTF-8");
    }

    private void assertJsonReturned(DefaultFullHttpResponse response, String expectedText, final String contentType) {
        final String actual = getBodyTextFromResponse(response);
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertEquals(contentType, response.headers().get("Content-Type"));
        assertEquals(expectedText, actual);
    }

    private DefaultFullHttpResponse invoke(Microservice microservice, String path) {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/admin/"+path);
        AdminFilter filter = new AdminFilter(request, microservice);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) filter.requestPre(request);
        return response;
    }

    private Microservice createLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(microcloud);
        return ms;
    }

    private String getBodyTextFromResponse(DefaultFullHttpResponse response) {
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < response.content().capacity(); i++) {
            actual.append((char) response.content().getByte(i));
        }
        return actual.toString();
    }
}
