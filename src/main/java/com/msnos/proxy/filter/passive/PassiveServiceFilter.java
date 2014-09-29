package com.msnos.proxy.filter.passive;

import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFiltersAdapter;

public class PassiveServiceFilter extends HttpFiltersAdapter {

    private Microservice microservice;

    public PassiveServiceFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        this.microservice = microservice;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        DefaultFullHttpResponse response = null;

        if (httpObject instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) httpObject;
            if (isPOSTAndContainsJson(request)) {
                PassiveRouter router = new PassiveRouter(microservice);
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
