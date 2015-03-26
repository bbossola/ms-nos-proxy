package com.msnos.proxy.filter.msnos;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import org.littleshoot.proxy.HttpFiltersAdapter;

import com.workshare.msnos.usvc.Microcloud;

public class PassiveServiceFilter extends HttpFiltersAdapter {

    private Microcloud microcloud;

    public PassiveServiceFilter(HttpRequest originalRequest, Microcloud ucloud) {
        super(originalRequest);
        this.microcloud = ucloud;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        DefaultFullHttpResponse response = null;

        if (httpObject instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) httpObject;
            if (isPOSTAndContainsJson(request)) {
                PassiveRouter router = new PassiveRouter(microcloud);
                if (hasValidUUID(request)) {
                    response = router.getPublishRestApiResponse(request);
                } else {
                    response = router.getServiceCreationResponse(request);
                }
            }
        }

        return response != null ? response : super.requestPre(httpObject);
    }

    private boolean hasValidUUID(HttpRequest request) {
        return request.getUri().matches("/msnos/1\\.0/pasv/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/apis");
    }

    private boolean isPOSTAndContainsJson(HttpRequest request) {
        return request.getMethod().equals(HttpMethod.POST) && request.headers() != null && request.headers().get("Content-Type").equals("application/json");
    }
}
