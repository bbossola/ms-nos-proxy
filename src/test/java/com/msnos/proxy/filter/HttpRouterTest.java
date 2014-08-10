package com.msnos.proxy.filter;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.junit.Before;
import org.junit.Test;

import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;

public class HttpRouterTest extends AbstractTest {

    @Before
    public void prepare() throws Exception {
        super.prepare();
    }

    @Test
    public void shouldRemoveServiceFromProxyingWhenFLTReceived() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/service/path");

        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102");

        HttpRouter router = new HttpRouter(request, microservice);
        simulateMessageFromCloud(new MessageBuilder(Message.Type.FLT, cloud, microservice.getAgent().getIden()).with(new FltPayload(remote.getAgent().getIden())).make());

        router.routeClient(request);
        HttpResponse response = router.serviceResponse(validHttpResponse());

        assertFalse(response.headers().contains(SET_COOKIE));
    }

    @Test
    public void shouldSendFinal500IfNoRestApis() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/service/path");
        Microservice microservice = createLocalMicroserviceAndJoinCloud();

        HttpRouter router = new HttpRouter(request, microservice);

        assertEquals("All endpoints for service/path are momentarily faulty", getBodyTextFromResponse((DefaultFullHttpResponse) router.serviceResponse(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))));
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


