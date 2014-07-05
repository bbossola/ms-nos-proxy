package com.msnos.proxy.filter;

import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import com.workshare.msnos.usvc.RestApi.Type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

class HttpRouter {
    private final Logger log = LoggerFactory.getLogger(HttpRouter.class);
    private final Microservice microservice;
    private final HttpRequest originalRequest;
    private final RetryLogic retry;

    private RestApi rest;
    private Set<Cookie> cookies;

    public HttpRouter(HttpRequest originalRequest, Microservice microservice) {
        this.originalRequest = originalRequest;
        this.microservice = microservice;
        retry = new RetryLogic();
    }

    public HttpResponse routeClient(HttpRequest request) {
        HttpResponse response = null;
        try {
            if (request.getUri().contains("admin/ping")) return pong();
            if (request.getUri().contains("admin/routes")) return routes();
            if (hasCorrectCookie(request)) {
                cookies = CookieDecoder.decode(request.headers().get(COOKIE));
                rest = routeCookiedRequest(request, cookies);
            } else {
                rest = routeRequest(request);
            }
            if (rest != null && rest.getType() == Type.PUBLIC) {
                if (rest.isFaulty()) {
                    response = createRetry();
                    if (hasCorrectCookie(request)) {
                        DefaultCookie cookie = createDeleteCookie(rest);
                        setCookieOnResponse(response, cookie);
                    }
                } else {
                    request.setUri(rest.getUrl());
                }
            } else {
                response = createResponse(NOT_FOUND);
            }
        } catch (Exception ex) {
            log.error("General exception: ", ex);
            response = createResponse(INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public HttpResponse serviceResponse(HttpResponse response) {
        if (response.getStatus().equals(HttpResponseStatus.INTERNAL_SERVER_ERROR) && rest != null) {
            if (rest.getTempFaults() < 4) rest.markTempFault();
            else rest.markFaulty();
        }
        if (retry.isWorth(response)) {
            response = createRetry();
        } else {
            if (rest != null && rest.hasAffinity()) {
                DefaultCookie cookie = createCookie(rest);
                if (cookies == null) cookies = new HashSet<Cookie>();
                if (!cookies.contains(cookie)) {
                    cookie.setPath("/");
                    setCookieOnResponse(response, cookie);
                }
            }
        }
        return response;
    }

    private RestApi routeRequest(HttpRequest httpRequest) throws Exception {

        String[] pathArray = getPathArray(httpRequest);
        if (pathArray.length < 3) return null;
        return microservice.searchApi(pathArray[1], pathArray[2]);
    }

    private HttpResponse routes() {
        StringBuilder builder = new StringBuilder();
        for (RestApi rest : microservice.getAllRemoteRestApis()) {
            if (rest.getType() == Type.HEALTHCHECK) continue;
            builder.append(Json.toJsonString(rest)).append("\n");
        }
        String resp = builder.toString();
        ByteBuf b = Unpooled.buffer(resp.length());
        DefaultFullHttpResponse routes = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK, b.writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
        routes.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return routes;
    }

    private HttpResponse pong() {
        String resp = "<h1>Pong</h1>";
        ByteBuf b = Unpooled.buffer(resp.length());
        DefaultFullHttpResponse pong = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK, b.writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
        pong.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return pong;
    }

    private RestApi routeCookiedRequest(HttpRequest httpRequest, Set<Cookie> cookies) throws Exception {
        RestApi result = null;
        for (Cookie cookie : cookies)
            if (cookie.getName().contains(createPath(httpRequest))) {
                result = microservice.searchApiById(Long.parseLong(cookie.getValue()));
                break;
            }
        return result;
    }

    private boolean hasCorrectCookie(HttpRequest httpRequest) throws URISyntaxException {
        return httpRequest.headers().get(COOKIE) != null && httpRequest.headers().get(COOKIE).contains(createPath(httpRequest));
    }

    private String encodeCookie(DefaultCookie cookie) {
        return ServerCookieEncoder.encode(cookie);
    }

    private DefaultCookie createCookie(RestApi rest) {
        return new DefaultCookie(String.format("x-%s/%s", rest.getName(), rest.getPath()), Long.toString(rest.getId()));
    }

    private DefaultCookie createDeleteCookie(RestApi rest) {
        DefaultCookie cookie = createCookie(rest);
        cookies.remove(cookie);
        cookie.setMaxAge(0);
        return cookie;
    }

    private String createPath(HttpRequest httpRequest) throws URISyntaxException {
        String[] pathArray = getPathArray(httpRequest);
        if (pathArray.length < 3) return "";
        return String.format("%s/%s", pathArray[1], pathArray[2]);
    }

    private String[] getPathArray(HttpRequest httpRequest) throws URISyntaxException {
        return new URI(httpRequest.getUri()).getPath().split("/");
    }

    private DefaultFullHttpResponse createResponse(HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    }

    private void setCookieOnResponse(HttpResponse response, DefaultCookie cookie) {
        response.headers().add(SET_COOKIE, encodeCookie(cookie));
    }

    private HttpResponse createRetry() {
        HttpResponse response;
        response = createResponse(FOUND);
        response.headers().add(LOCATION, originalRequest.getUri());
        return response;
    }
}
