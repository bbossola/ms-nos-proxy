package com.msnos.proxy.filter;

import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.littleshoot.proxy.HttpFiltersAdapter;

public class HttpProxyFilter extends HttpFiltersAdapter {

    private final HttpRouter router;

    public HttpProxyFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        router = new HttpRouter(originalRequest, microservice);
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) httpObject;
            response = router.routeClient(request);
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;
            return router.serviceResponse(response);
        }
        return httpObject;
    }
}
