package com.msnos.proxy.filter;

import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Created by rhys on 28/07/14.
 */
public class AdminFilterTest extends AbstractTest {

    @Before
    public void prepare() throws Exception {
        super.prepare();
    }

    @Test
    public void shouldReturnPongWhenAdminPingInURI() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/admin/ping");
        Microservice microservice = mock(Microservice.class);

        AdminFilter filter = new AdminFilter(request, microservice);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) filter.requestPre(request);

        String expected = "<h1>Pong</h1>";
        String actual = getBodyTextFromResponse(response);
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertEquals(expected, actual);
    }

    @Test
    public void shouldReturnListWhenAdminRoutesInURI() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/admin/routes");
        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102/25");
        setupRemoteMicroserviceWithAffinity("other", "diff", "82.20.230.102/25");

        AdminFilter filter = new AdminFilter(request, microservice);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) filter.requestPre(request);

        String actual = getBodyTextFromResponse(response);
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertTrue(actual.contains("name"));
        assertTrue(actual.contains("path"));
        assertTrue(actual.contains("port"));
        assertTrue(actual.contains("host"));
        assertTrue(actual.contains("location"));
        assertTrue(actual.contains("sessionAffinity"));
    }

    private Microservice createLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(cloud);
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
