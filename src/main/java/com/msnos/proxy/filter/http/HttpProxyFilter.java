package com.msnos.proxy.filter.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.usvc.Microservice;

public class HttpProxyFilter extends HttpFiltersAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyFilter.class);

    private final HttpRouter router;

    public HttpProxyFilter(HttpRequest originalRequest, ChannelHandlerContext context, Microservice microservice) {
        super(originalRequest);
        router = new HttpRouter(originalRequest, context, microservice);
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        if (log.isDebugEnabled()) log.debug("http: {}", httpObject);

        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) httpObject;
            response = router.computeApiRoute(request);
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        if (log.isDebugEnabled()) log.debug("http: {}", httpObject);

        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;
            return router.handleApiResponse(response);
        }
        return httpObject;
    }
}
