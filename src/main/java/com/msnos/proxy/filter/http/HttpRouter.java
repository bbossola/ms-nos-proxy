package com.msnos.proxy.filter.http;

import com.msnos.proxy.filter.HttpRetry;
import com.msnos.proxy.filter.Retry;
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
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

class HttpRouter {

    private static final Logger log = LoggerFactory.getLogger(HttpRouter.class);
    public static final String[] EMPTY_PATH = new String[]{};

    private final Microservice microservice;
    private final HttpRequest originalRequest;
    private final Retry retry;
    private final int tempRetries;

    private RestApi rest;
    private Set<Cookie> cookies;

    public HttpRouter(HttpRequest originalRequest, Microservice microservice) {
        this.originalRequest = originalRequest;
        this.microservice = microservice;
        this.tempRetries = Integer.getInteger("temporary.api.retries", 4);
        this.retry = new HttpRetry();
    }

    public HttpResponse routeRequest(HttpRequest request) {

        try {
            if (cookieMatchesUri(request)) {
                cookies = decodeCookies(request);
                rest = findApiWithCookie(request, cookies);
            } else {
                rest = findApi(request);
            }

            if (rest == null || rest.getType() != Type.PUBLIC) {
                log.debug("Request search found no suitable rest api for {} ", request.getUri());
                return createResponse(NOT_FOUND);
            }

            if (rest.isFaulty())
                return createRetryResponse();

            request.setUri(rest.getUrl());
            return null;
        } catch (Exception ex) {
            log.error("General exception requesting " + request.getUri(), ex);
            return createResponse(INTERNAL_SERVER_ERROR);
        }
    }

    public HttpResponse handleResponse(HttpResponse response) {

        if (faultyResponseAndNoOtherRestApi(response))
            return noWorkingRestApiResponse();

        if (rest == null)
            return response;

        if (retry.isNeeded(response)) {
            markApiFaultyStatus();

            response = createRetryResponse();
            DefaultCookie cookie = createDeleteCookie(rest);
            setCookieOnResponse(response, cookie);
        } else {
            if (rest.hasAffinity()) {
                DefaultCookie cookie = createCookie(rest);
                if (cookies == null || !cookies.contains(cookie)) {
                    setCookieOnResponse(response, cookie);
                }
            }
        }

        return response;
    }

    private void markApiFaultyStatus() {
        if (rest.getTempFaults() < tempRetries) {
            rest.markTempFault();
        } else {
            rest.markFaulty();
        }
    }

    private RestApi findApi(HttpRequest httpRequest) {
        String[] pathArray = getPathArray(httpRequest);
        if (pathArray.length < 3)
            return null;

        return microservice.searchApi(pathArray[1], pathArray[2]);
    }

    private RestApi findApiWithCookie(HttpRequest httpRequest, Set<Cookie> cookies) throws Exception {
        RestApi result = null;
        String path = createPath(httpRequest);
        for (Cookie cookie : cookies) {
            if (cookie.getName().contains(path)) {
                try {
                    result = microservice.getCloud().searchApiById(Long.parseLong(cookie.getValue()));
                    break;
                } catch (NumberFormatException e) {
                    log.error("Invalid value for cookie {}", cookie.toString());
                    cookies.remove(cookie);
                }
            }
        }
        return result;
    }

    private boolean faultyResponseAndNoOtherRestApi(HttpResponse response) {
        return response.getStatus().equals(INTERNAL_SERVER_ERROR) && findApi(originalRequest) == null;
    }

    private boolean cookieMatchesUri(HttpRequest httpRequest) throws URISyntaxException {
        return httpRequest.headers().get(COOKIE) != null && httpRequest.headers().get(COOKIE).contains(createPath(httpRequest));
    }

    private ConcurrentSkipListSet<Cookie> decodeCookies(HttpRequest request) {
        return new ConcurrentSkipListSet<Cookie>(CookieDecoder.decode(request.headers().get(COOKIE)));
    }

    private String encodeCookie(DefaultCookie cookie) {
        return ServerCookieEncoder.encode(cookie);
    }

    private void setCookieOnResponse(HttpResponse response, DefaultCookie cookie) {
        response.headers().add(SET_COOKIE, encodeCookie(cookie));
    }

    private DefaultCookie createCookie(RestApi rest) {
        DefaultCookie cookie = new DefaultCookie(String.format("x-%s/%s", rest.getName(), rest.getPath()), Long.toString(rest.getId()));
        cookie.setPath("/");
        return cookie;
    }

    private DefaultCookie createDeleteCookie(RestApi rest) {
        DefaultCookie cookie = createCookie(rest);
        if (cookies != null && cookies.contains(cookie)) cookies.remove(cookie);
        cookie.setMaxAge(0);
        return cookie;
    }

    private String createPath(HttpRequest httpRequest) throws URISyntaxException {
        String[] pathArray = getPathArray(httpRequest);
        if (pathArray.length < 3) return "";
        return String.format("%s/%s", pathArray[1], pathArray[2]);
    }

    private String[] getPathArray(HttpRequest httpRequest) {
        try {
            return new URI(httpRequest.getUri()).getPath().split("/");
        } catch (URISyntaxException e) {
            log.warn("Unable to split request {}", httpRequest.getUri());
            return EMPTY_PATH;
        }
    }

    private DefaultFullHttpResponse createResponse(HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HTTP_1_1, status);
    }

    private HttpResponse noWorkingRestApiResponse() {
        String respString = String.format("All endpoints for %s are momentarily faulty", originalRequest.getUri());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT, writeContent(respString));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return response;
    }

    private HttpResponse createRetryResponse() {
        HttpResponse response;
        response = createResponse(FOUND);
        response.headers().add(LOCATION, originalRequest.getUri());
        return response;
    }

    private ByteBuf writeContent(String respString) {
        return Unpooled.buffer(respString.length()).writeBytes(respString.getBytes(CharsetUtil.UTF_8));
    }
}
