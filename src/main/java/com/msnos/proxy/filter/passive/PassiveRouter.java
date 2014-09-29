package com.msnos.proxy.filter.passive;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.workshare.msnos.core.MsnosException;
import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.PassiveService;
import com.workshare.msnos.usvc.api.RestApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class PassiveRouter {

    private Microservice microservice;

    public PassiveRouter(Microservice microservice) {
        this.microservice = microservice;
    }

    public DefaultFullHttpResponse getServiceCreationResponse(FullHttpRequest request) {
        try {
            PassiveService passive = createPassiveService(getAsJsonObject(getRequestContentAsString(request)));
            passive.join();
            return createSubscriptionResponse(passive.getUuid());
        } catch (IllegalArgumentException e) {
            return createResponse(BAD_REQUEST, "text/plain", e.getMessage());
        } catch (JsonParseException e) {
            return createResponse(NOT_ACCEPTABLE, "text/plain", e.getMessage());
        } catch (MsnosException e) {
            return createResponse(INTERNAL_SERVER_ERROR, "text/plain", e.getMessage());
        }
    }

    public DefaultFullHttpResponse getPublishRestApiResponse(FullHttpRequest request) {
        String uuidString;

        try {
            uuidString = stripOutUUIDFromURI(request);
        } catch (IllegalArgumentException e) {
            return createResponse(BAD_REQUEST, "text/plain", e.getMessage());
        }

        PassiveService passiveService = microservice.searchPassives(UUID.fromString(uuidString));
        if (passiveService == null) {
            return createResponse(NOT_FOUND, "text/plain", "No passive service found for supplied UUID. ");
        }

        RestApi restApi;
        try {
            restApi = createRestApiFromJson(request);
        } catch (JsonParseException e) {
            return createResponse(NOT_ACCEPTABLE, "text/plain", e.getMessage());
        }

        try {
            passiveService.publish(restApi);
        } catch (MsnosException e) {
            return createResponse(INTERNAL_SERVER_ERROR, "text/plain", e.getMessage());
        }

//      FIXME
        return createResponse(OK, "application/json", "{\"uuid\":\"" + restApi.getId() + "\"}");
    }

    private RestApi createRestApiFromJson(FullHttpRequest request) {
        try {
            JsonObject jsonObject = getAsJsonObject(getRequestContentAsString(request));
            String name = getElementAsString(jsonObject, "name");
            String path = getElementAsString(jsonObject, "path");
            int port = jsonObject.get("port").getAsInt();
            String host = getElementAsString(jsonObject, "host");
            RestApi.Type type = RestApi.Type.valueOf(getElementAsString(jsonObject, "type"));
            boolean sessionAffinity = jsonObject.get("affinity").getAsBoolean();

            return new RestApi(name, path, port, host, type, sessionAffinity);
        } catch (Exception e) {
            throw new JsonParseException("Could not correctly obtain the JSON for creation of Rest Api to be published. ");
        }
    }

    private String stripOutUUIDFromURI(HttpRequest request) throws IllegalArgumentException {
        String[] uriArray = request.getUri().toLowerCase().split("/");

        for (String uri : uriArray) {
            if (uri.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return uri;
        }

        throw new IllegalArgumentException("Unable to retrieve UUID from request URI. Returning null, please try again. ");
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

    private DefaultFullHttpResponse createSubscriptionResponse(UUID uuid) {
        return createResponse(OK, "application/json", "{\"uuid\":" + Json.toJsonString(uuid) + "}");
    }

    private DefaultFullHttpResponse createResponse(HttpResponseStatus status, String contentType, String message) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status);
        response.headers().set(CONTENT_TYPE, contentType);
        writeContent(response, message);
        return response;
    }

    private String getRequestContentAsString(FullHttpRequest request) {
        return request.content().toString(CharsetUtil.UTF_8);
    }

    private JsonObject getAsJsonObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private String getElementAsString(JsonObject jsonObj, String cloud) {
        return jsonObj.get(cloud).getAsString();
    }

    private ByteBuf writeContent(DefaultFullHttpResponse response, String resp) {
        return response.content().writeBytes(Unpooled.buffer(resp.length()).writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
    }
}
