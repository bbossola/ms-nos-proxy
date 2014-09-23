package com.msnos.proxy.filter.http;

import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyFilter extends HttpFiltersAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyFilter.class);

    private final HttpRouter router;

    public HttpProxyFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        router = new HttpRouter(originalRequest, microservice);
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        if (log.isDebugEnabled()) log.debug("http: " + Json.toJsonString(httpObject));

        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) httpObject;
            response = router.routeRequest(request);
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        if (log.isDebugEnabled()) log.debug("http: " + Json.toJsonString(httpObject));

        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;
            return router.handleResponse(response);
        }
        return httpObject;
    }
}
