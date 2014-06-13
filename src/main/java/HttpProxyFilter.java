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

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

public class HttpProxyFilter extends HttpFiltersAdapter {

    private final Logger log = LoggerFactory.getLogger(HttpProxyFilter.class);
    private final Microservice microservice;

    private RestApi rest;
    private Set<Cookie> cookies;

    public HttpProxyFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        this.microservice = microservice;
        System.out.println(this.hashCode());
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
                    request.setUri(rest.getUrl());
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
            if (rest.hasAffinity()) {
                DefaultCookie cookie = createResponseCookie(rest);
                if (cookies == null) cookies = new HashSet<Cookie>();
                if (!cookies.contains(cookie)) {
                    cookie.setPath("/");
                    response.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookie));
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
            if (cookie.getName().contains(createPathString(httpRequest))) {
                result = microservice.searchApiById(Long.parseLong(cookie.getValue()));
                break;
            }
        return result;
    }

    private boolean hasCorrectCookie(HttpRequest httpRequest) throws URISyntaxException {
        return httpRequest.headers().get(COOKIE) != null && httpRequest.headers().get(COOKIE).contains(createPathString(httpRequest));
    }

    private DefaultCookie createResponseCookie(RestApi rest) {
        return new DefaultCookie(String.format("x-%s/%s", rest.getName(), rest.getPath()), Long.toString(rest.getId()));
    }

    private String createPathString(HttpRequest httpRequest) throws URISyntaxException {
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

