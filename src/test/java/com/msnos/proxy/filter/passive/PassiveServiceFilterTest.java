package com.msnos.proxy.filter.passive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonParser;
import com.msnos.proxy.filter.AbstractTest;
import com.msnos.proxy.filter.msnos.PassiveServiceFilter;
import com.workshare.msnos.usvc.Microservice;

public class PassiveServiceFilterTest extends AbstractTest {

    private Microservice microservice;
    private DefaultFullHttpRequest request;
    private DefaultFullHttpResponse httpResponse;
    private PassiveServiceFilter passiveServiceFilter;

    @Before
    public void setUp() throws Exception {
        super.prepare();
        microservice = new Microservice("test");
        microservice.join(microcloud);
    }

    @Test
    public void shouldReturnUUIDWhenSubscribeMessage() throws Exception {
        request = getSubRequestWithCorrectJson();

        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        String expected = "{\"uuid\":";

        assertTrue(httpResponse != null);
        assertEquals(200, httpResponse.getStatus().code());
        assertTrue(getResponseString(httpResponse).contains(expected));
    }

//    @Test
//    public void shouldReturn400IfSubRequestIsForOtherCloud() throws Exception {
//        request = getSubRequestWithCorrectJson();
//
//        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);
//
//        assertEquals(400, httpResponse.getStatus().code());
//        assertEquals(("Passive microservice trying to join different cloud than microservice's joined cloud! "), getResponseString(httpResponse));
//    }

    @Test
    public void shouldReturnNotAcceptableIfJSONUnparsable() throws Exception {
        request = getSubRequestWithWrongJson();

        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(406, httpResponse.getStatus().code());
        assertEquals(("Could not correctly obtain the JSON for creation of Passive Service. "), getResponseString(httpResponse));
    }

    @Test
    public void shouldReturnNotAcceptableIfJSONBroken() throws Exception {
        request = getSubRequestWithBrokenJson();

        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertEquals(406, httpResponse.getStatus().code());
        assertEquals(("com.google.gson.stream.MalformedJsonException: Use JsonReader.setLenient(true) to accept malformed JSON at line 1 column 9"), getResponseString(httpResponse));
    }

    @Test
    public void shouldReturnUUIDOnCorrectRestApiPubRequest() throws Exception {
        request = getSubRequestWithCorrectJson();
        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        String uuidResponse = getUUIDFromResponse();

        request = getPubRequestWithCorrectApiJson(uuidResponse);
        httpResponse = (DefaultFullHttpResponse) passiveServiceFilter().requestPre(request);

        assertTrue(getResponseString(httpResponse).contains("{\"uuid\":"));
    }

    private String getUUIDFromResponse() {
        return new JsonParser().parse(httpResponse.content().toString(CharsetUtil.UTF_8)).getAsJsonObject().get("uuid").getAsString();
    }

    private PassiveServiceFilter passiveServiceFilter() {
        if (passiveServiceFilter == null)
            return passiveServiceFilter = new PassiveServiceFilter(request, microcloud);
        else
            return passiveServiceFilter;
    }

    private String getResponseString(DefaultFullHttpResponse httpResponse) {
        return httpResponse.content().toString(Charset.forName("UTF-8"));
    }

    private DefaultFullHttpRequest getPubRequestWithCorrectApiJson(String uuidResponse) {
        return getPubRequestWithJson("{\"name\":\"someName\", " +
                "\"path\":\"path\", " +
                "\"type\":\"PUBLIC\"," +
                "\"host\":\"10.10.10.10\"," +
                "\"port\":\"9999\"," +
                "\"affinity\":\"true\"}", uuidResponse);
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

    private DefaultFullHttpRequest getPubRequestWithJson(String jsonString, String uuidString) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/msnos/1.0/pasv/" + uuidString + "/apis");
        request.headers().add("Content-Type", "application/json");
        request.content().writeBytes(jsonString.getBytes(Charset.forName("UTF-8")));
        return request;
    }

    private DefaultFullHttpRequest getSubRequestWithJson(String jsonString) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/msnos/1.0/pasv");
        request.headers().add("Content-Type", "application/json");
        request.content().writeBytes(jsonString.getBytes(Charset.forName("UTF-8")));
        return request;
    }
}