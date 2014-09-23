package com.msnos.proxy.filter.passive;

import com.msnos.proxy.filter.AbstractTest;
import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PassiveServiceFilterTest extends AbstractTest {

    private Microservice microservice;
    private DefaultFullHttpRequest request;

    @Before
    public void setUp() throws Exception {
        microservice = new Microservice("test");
    }

    @Test
    public void shouldReturnUUIDWhenSubscribeMessage() throws Exception {
        microservice.join(new Cloud(UUID.fromString("078d9596-3f1f-11e4-9d9f-164230df67")));
        request = getSubRequestWithCorrectJson();

        DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        String expected = "{\"uuid\":";

        assertTrue(httpResponse != null);
        assertEquals(200, httpResponse.getStatus().code());
        assertTrue(getResponseString(httpResponse).contains(expected));
    }

    @Test
    public void shouldReturn400IfSubRequestIsForOtherCloud() throws Exception {
        microservice.join(new Cloud(new UUID(111, 222)));
        request = getSubRequestWithCorrectJson();

        DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(400, httpResponse.getStatus().code());
        assertEquals(("Passive microservice trying to join different cloud than microservice's joined cloud! "), getResponseString(httpResponse));
    }

    @Test
    public void shouldReturn400IfPassiveServiceRequestToUnjoinedMicroservice() throws Exception {
        request = getSubRequestWithCorrectJson();

        DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(400, httpResponse.getStatus().code());
        assertEquals(("Microservice not currently joined to a cloud, passive service creation refused! "), getResponseString(httpResponse));
    }


    @Test
    public void shouldReturnNotAcceptableIfJSONUnparsable() throws Exception {
        microservice.join(new Cloud(UUID.fromString("078d9596-3f1f-11e4-9d9f-164230df67")));
        request = getSubRequestWithWrongJson();

        DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(406, httpResponse.getStatus().code());
        assertEquals(("Could not correctly obtain the JSON for creation of Passive Service. "), getResponseString(httpResponse));
    }

    @Test
    public void shouldReturnNotAcceptableIfJSONBroken() throws Exception {
        microservice.join(new Cloud(UUID.fromString("078d9596-3f1f-11e4-9d9f-164230df67")));
        request = getSubRequestWithBrokenJson();

        DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(406, httpResponse.getStatus().code());
        assertEquals(("com.google.gson.stream.MalformedJsonException: Use JsonReader.setLenient(true) to accept malformed JSON at line 1 column 9"), getResponseString(httpResponse));
    }


    private PassiveServiceFilter passiveServiceFilter() {
        return new PassiveServiceFilter(request, microservice);
    }

    private String getResponseString(DefaultFullHttpResponse httpResponse) {
        return httpResponse.content().toString(Charset.forName("UTF-8"));
    }

    private DefaultFullHttpRequest getSubRequestWithCorrectJson() {
        return getSubRequestWithJson("{\"cloud\":\"078d9596-3f1f-11e4-9d9f-164230df67\", " +
                "\"service\":\"name\", " +
                "\"host\":\"10.10.10.10\"," +
                "\"port\":\"9999\"," +
                "\"healthcheck-uri\":\"19.19.19.12:7777/healthcheck/sup\"}");
    }

    private DefaultFullHttpRequest getSubRequestWithWrongJson() {
        return getSubRequestWithJson("{\"cloud\":\"078d9596-3f1f-11e4-9d9f-164230df67\", " +
                "\"serqweice\":\"name\", " +
                "\"hosqqt\":\"10.10.10.10\"," +
                "\"healthcheck-uri\":\"19.19.19.12:7777/healthcheck/sup\"}");
    }

    private DefaultFullHttpRequest getSubRequestWithBrokenJson() {
        return getSubRequestWithJson("\"cloud\"\"078d9596-3f1f-11e4-9d9f-164230df67\", " +
                "\"serqweice\":\"name\" " +
                "\"hosqqt\":\"10.10.10.10\"," +
                "\"port\":\"9999\"," +
                "\"healthcheck-uri\":\"19.19.19.12:7777/healthcheck/sup\"}");
    }

    private DefaultFullHttpRequest getSubRequestWithJson(String jsonString) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/msnos/1.0/pasv");
        request.headers().add("Content-Type", "application/json");
        request.content().writeBytes(jsonString.getBytes(Charset.forName("UTF-8")));
        return request;
    }

}