package com.msnos.proxy.filter;

import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;
import com.workshare.msnos.usvc.api.RestApi.Type;
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
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

class HttpRouter {

    private static final Logger log = LoggerFactory.getLogger(HttpRouter.class);

    private final Microservice microservice;
    private final HttpRequest originalRequest;
    private final RetryLogic retry;
    private final int tempRetries;

    private RestApi rest;
    private Set<Cookie> cookies;

    public HttpRouter(HttpRequest originalRequest, Microservice microservice) {
        this.originalRequest = originalRequest;
        this.microservice = microservice;
        this.tempRetries = Integer.parseInt(System.getProperty("temporary.api.retries", "4"));
        this.retry = new RetryLogic();
    }

    public HttpResponse routeClient(HttpRequest request) {
        HttpResponse response = null;
        try {
            if (hasCorrectCookie(request)) {
                cookies = CookieDecoder.decode(request.headers().get(COOKIE));
                rest = routeCookiedRequest(request, cookies);
            } else {
                rest = routeRequest(request);
            }
            if (rest != null && rest.getType() == Type.PUBLIC) {
                if (!rest.isFaulty()) {
                    request.setUri(rest.getUrl());
                } else {
                    response = createRetry();
                    if (hasCorrectCookie(request)) {
                        DefaultCookie cookie = createDeleteCookie(rest);
                        setCookieOnResponse(response, cookie);
                    }
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
        if (response != null) {
            if (response.getStatus().equals(HttpResponseStatus.INTERNAL_SERVER_ERROR) && rest != null) {
                if (rest.getTempFaults() < tempRetries) rest.markTempFault();
                else rest.markFaulty();
            }
            try {
                if (faultyResponseAndNoOtherRestApi(response)) {
                    return noWorkingRestApiResponse();
                }
            } catch (Exception e) {
                log.error("Error returning momentarily faulty response", e);
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
        } else {
            try {
                response = noWorkingRestApiResponse();
            } catch (URISyntaxException e) {
                log.error("Error returning momentarily faulty response", e);
                response = createResponse(INTERNAL_SERVER_ERROR);
            }
        }
        return response;
    }

    private boolean faultyResponseAndNoOtherRestApi(HttpResponse response) throws Exception {
        return response.getStatus().equals(INTERNAL_SERVER_ERROR) && routeRequest(originalRequest) == null;
    }

    private HttpResponse noWorkingRestApiResponse() throws URISyntaxException {
        String respString = String.format("All endpoints for %s are momentarily faulty", originalRequest.getUri());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT, writeContent(respString));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return response;
    }

    private ByteBuf writeContent(String respString) {
        return Unpooled.buffer(respString.length()).writeBytes(respString.getBytes(CharsetUtil.UTF_8));
    }

    private RestApi routeRequest(HttpRequest httpRequest) throws Exception {
        String[] pathArray = getPathArray(httpRequest);
        if (pathArray.length < 3) return null;
        return microservice.searchApi(pathArray[1], pathArray[2]);
    }

    private RestApi routeCookiedRequest(HttpRequest httpRequest, Set<Cookie> cookies) throws Exception {
        RestApi result = null;
        for (Cookie cookie : cookies)
            if (cookie.getName().contains(createPath(httpRequest))) {
                try {
                    result = microservice.searchApiById(Long.parseLong(cookie.getValue()));
                    break;
                } catch (NumberFormatException e) {
                    log.error("Invalid value for cookie {}", cookie.toString());
                    cookies.remove(cookie);
                }
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
        return new DefaultFullHttpResponse(HTTP_1_1, status);
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
