package com.msnos.proxy.filter.passive;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.PassiveService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.littleshoot.proxy.HttpFiltersAdapter;

import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

public class PassiveServiceFilter extends HttpFiltersAdapter {

    private Microservice microservice;

    public PassiveServiceFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        this.microservice = microservice;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        DefaultFullHttpResponse response = null;

        if (httpObject instanceof HttpRequest) {
            DefaultFullHttpRequest request = (DefaultFullHttpRequest) httpObject;
            if (isPOSTAndContainsJson(request)) {
                try {
                    PassiveService passive = createPassiveService(getAsJsonObject(getRequestContentAsString(request)));
                    passive.join();
                    response = createSubscriptionResponse(passive.getUuid());
                } catch (IllegalArgumentException e) {
                    response = createResponse(HttpResponseStatus.BAD_REQUEST, "text/plain", e.getMessage());
                } catch (JsonParseException e) {
                    response = createResponse(HttpResponseStatus.NOT_ACCEPTABLE, "text/plain", e.getMessage());
                }
            }
        }

        return response != null ? response : super.requestPre(httpObject);
    }

    private boolean isPOSTAndContainsJson(DefaultFullHttpRequest request) {
        return request.getMethod().equals(HttpMethod.POST) && request.headers() != null && request.headers().get("Content-Type").equals("application/json");
    }

    private JsonObject getAsJsonObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private String getRequestContentAsString(DefaultFullHttpRequest request) {
        return request.content().toString(CharsetUtil.UTF_8);
    }

    private PassiveService createPassiveService(JsonObject jsonObj) throws IllegalArgumentException, JsonParseException {
        try {
            UUID cloudUUID = UUID.fromString(getElementAsString(jsonObj, "cloud"));
            String name = getElementAsString(jsonObj, "service");
            String host = getElementAsString(jsonObj, "host");
            String healthcheck = getElementAsString(jsonObj, "healthcheck-uri");
            int port = jsonObj.get("port").getAsInt();

            return new PassiveService(microservice, cloudUUID, name, host, healthcheck, port);
        } catch (NullPointerException e) {
            throw new JsonParseException("Could not correctly obtain the JSON for creation of Passive Service. ");
        }
    }

    private String getElementAsString(JsonObject jsonObj, String cloud) {
        return jsonObj.get(cloud).getAsString();
    }

    private DefaultFullHttpResponse createResponse(HttpResponseStatus status, String contentType, String message) {
        DefaultFullHttpResponse response;
        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(CONTENT_TYPE, contentType);
        writeContent(response, message);
        return response;
    }

    private DefaultFullHttpResponse createSubscriptionResponse(UUID uuid) {
        return createResponse(HttpResponseStatus.OK, "application/json", "{\"uuid\":\"" + Json.toJsonString(uuid));
    }

    private ByteBuf writeContent(DefaultFullHttpResponse response, String resp) {
        return response.content().writeBytes(Unpooled.buffer(resp.length()).writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
    }
}
