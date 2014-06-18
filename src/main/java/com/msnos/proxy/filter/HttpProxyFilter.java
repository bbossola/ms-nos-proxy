package com.msnos.proxy.filter;

import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class HttpProxyFilter extends HttpFiltersAdapter {

    private final Logger log = LoggerFactory.getLogger(HttpProxyFilter.class);
    private final HttpRequest originalRequest;
    private final Microservice microservice;
    private final RetryLogic retry;

    private RestApi rest;
    private Set<Cookie> cookies;

    public HttpProxyFilter(HttpRequest originalRequest, Microservice microservice, RetryLogic retry) {
        super(originalRequest);
        this.originalRequest = originalRequest;
        this.microservice = microservice;
        this.retry = retry;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) httpObject;
            try {
                if (hasCorrectCookie(request)) {
                    cookies = CookieDecoder.decode(request.headers().get(COOKIE));
                    rest = routeCookiedRequest(request, cookies);
                } else {
                    rest = routeRequest(request);
                }
                if (rest != null) {
                    if (rest.isFaulty()) {
                        response = createResponse(FOUND);
                        response.headers().add(LOCATION, originalRequest.getUri());
                        if (hasCorrectCookie(request)) cookies.remove(createCookie(rest));
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
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;
            if (retry.isWorth(response)) {
                response.setStatus(FOUND);
                response.headers().add(LOCATION, originalRequest.getUri());
            } else {
                if (rest != null && rest.hasAffinity()) {
                    DefaultCookie cookie = createCookie(rest);
                    if (cookies == null) cookies = new HashSet<Cookie>();
                    if (!cookies.contains(cookie)) {
                        cookie.setPath("/");
                        response.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookie));
                    }
                }
            }
            return response;
        }
        return httpObject;
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
                result = microservice.searchApiById(Long.parseLong(cookie.getValue()));
                break;
            }
        return result;
    }

    private boolean hasCorrectCookie(HttpRequest httpRequest) throws URISyntaxException {
        return httpRequest.headers().get(COOKIE) != null && httpRequest.headers().get(COOKIE).contains(createPath(httpRequest));
    }

    private DefaultCookie createCookie(RestApi rest) {
        return new DefaultCookie(String.format("x-%s/%s", rest.getName(), rest.getPath()), Long.toString(rest.getId()));
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
}
