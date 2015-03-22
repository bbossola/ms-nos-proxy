package com.msnos.proxy.filter.http;

import com.msnos.proxy.filter.HttpRetry;
import com.msnos.proxy.filter.Retry;
import com.workshare.msnos.usvc.Microcloud;
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

    private static final int MAX_FAILURES = Integer.getInteger("com.msnos.proxy.api.failures.max", 4);

    public static final String COOKIE_PREFIX = "x-msnos-";
    public static final String COOKIE_API_ID_FORMAT = COOKIE_PREFIX+"%s";
    public static final String[] EMPTY_PATH = new String[]{};

    private static final Retry RETRY = new HttpRetry();

    private final Microcloud microcloud;
    private final Microservice microservice;
    private final String path;

    private RestApi api;
    private Set<Cookie> cookies;

    public HttpRouter(HttpRequest originalRequest, Microservice microservice) {
        this.microcloud = microservice.getCloud();
        this.microservice = microservice;
        this.path = extractPath(originalRequest);
    }

    public HttpResponse computeApiRoute(HttpRequest request) {

        try {
            boolean affinity = cookieMatchesUri(request);
            if (affinity) {
                cookies = decodeCookies(request);
                api = findApiWithCookie(request, cookies);
            } 
            
            if (api == null) {
                api = microservice.searchApi(path);
            }

            if (api != null && api.getType() != Type.PUBLIC) {
                log.warn("An attempt to call the restricted api {} was done ", api);
                return createResponse(NOT_FOUND);
            }
            
            if (api == null) {
                if (microcloud.canServe(path)) {
                    log.info("A suitable rest api for {} is present but it's not working :(", request.getUri());
                    return createResponse(BAD_GATEWAY);
                } else {
                    log.debug("Request search found no suitable rest api for {} ", request.getUri());
                    return createResponse(NOT_FOUND);
                }
            }
            
            if (api.isFaulty()) {
                if (affinity)
                    return createResponse(BAD_GATEWAY);
                else
                    return createRetryResponse();
            }

            request.setUri(api.getUrl());
            return null;
        } catch (Exception ex) {
            log.error("General exception requesting " + request.getUri(), ex);
            return createResponse(INTERNAL_SERVER_ERROR);
        }
    }

    public HttpResponse handleApiResponse(HttpResponse response) {

        if (api == null)
            return response;

        if (RETRY.isNeeded(response)) {
            markApiFaultyStatus();

            if (microservice.searchApi(path) == null) {
                response = noWorkingRestApiResponse();
            } else {
                response = createRetryResponse();
                DefaultCookie cookie = createDeleteCookie(api);
                setCookieOnResponse(response, cookie);
            }
        } else {
            if (api.hasAffinity()) {
                DefaultCookie cookie = createCookie(api);
                if (cookies == null || !cookies.contains(cookie)) {
                    setCookieOnResponse(response, cookie);
                }
            }
        }

        return response;
    }

    private void markApiFaultyStatus() {
        if (api.getTempFaults() < MAX_FAILURES) {
            api.markTempFault();
        } else {
            api.markFaulty();
        }
    }

    private RestApi findApiWithCookie(HttpRequest httpRequest, Set<Cookie> cookies) throws Exception {
        RestApi result = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().contains(path)) {
                try {
                    result = microcloud.searchApiById(Long.parseLong(cookie.getValue()));
                    break;
                } catch (NumberFormatException e) {
                    log.error("Invalid value for cookie {}", cookie.toString());
                    cookies.remove(cookie);
                }
            }
        }
        
        return result == null ? null : (result.isFaulty() ? null : result);
    }

    private boolean cookieMatchesUri(HttpRequest httpRequest) throws URISyntaxException {
        final String cookieHeader = httpRequest.headers().get(COOKIE);
        return cookieHeader != null && cookieHeader.contains(path);
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
        DefaultCookie cookie = new DefaultCookie(String.format(COOKIE_API_ID_FORMAT, rest.getPath()), Long.toString(rest.getId()));
        cookie.setPath("/");
        return cookie;
    }

    private DefaultCookie createDeleteCookie(RestApi rest) {
        DefaultCookie cookie = createCookie(rest);
        if (cookies != null && cookies.contains(cookie)) cookies.remove(cookie);
        cookie.setMaxAge(0);
        return cookie;
    }

    private String extractPath(HttpRequest httpRequest) {
        try {
            return new URI(httpRequest.getUri()).getPath();
        } catch (URISyntaxException e) {
            log.warn("Unable to split request {}", httpRequest.getUri());
            return "----------------------";
        }
    }

    private DefaultFullHttpResponse createResponse(HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HTTP_1_1, status);
    }

    private HttpResponse noWorkingRestApiResponse() {
        String respString = String.format("All endpoints for %s are momentarily faulty", path);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, writeContent(respString));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return response;
    }

    private HttpResponse createRetryResponse() {
        HttpResponse response;
        response = createResponse(FOUND);
        response.headers().add(LOCATION, path);
        return response;
    }

    private ByteBuf writeContent(String respString) {
        return Unpooled.buffer(respString.length()).writeBytes(respString.getBytes(CharsetUtil.UTF_8));
    }
}
