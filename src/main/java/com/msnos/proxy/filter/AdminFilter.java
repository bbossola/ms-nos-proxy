package com.msnos.proxy.filter;

import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;
import org.littleshoot.proxy.HttpFiltersAdapter;

import static com.workshare.msnos.usvc.RestApi.Type;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class AdminFilter extends HttpFiltersAdapter {
    private final HttpRequest request;
    private final Microservice microservice;

    public AdminFilter(HttpRequest request, Microservice microservice) {
        super(request);
        this.request = request;
        this.microservice = microservice;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            if (request.getUri().contains("admin/routes")) response = routes();
            if (request.getUri().contains("admin/ping")) response = pong();
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        return httpObject;
    }

    private HttpResponse routes() {
        StringBuilder builder = new StringBuilder();
        for (RestApi rest : microservice.getAllRemoteRestApis()) {
            if (rest.getType() == Type.HEALTHCHECK) continue;
            builder.append(Json.toJsonString(rest)).append("\n");
        }
        String resp = builder.toString();
        ByteBuf b = Unpooled.buffer(resp.length());
        DefaultFullHttpResponse routes = new DefaultFullHttpResponse(HTTP_1_1, OK, b.writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
        routes.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return routes;
    }

    private HttpResponse pong() {
        String resp = "<h1>Pong</h1>";
        ByteBuf b = Unpooled.buffer(resp.length());
        DefaultFullHttpResponse pong = new DefaultFullHttpResponse(HTTP_1_1, OK, b.writeBytes(resp.getBytes(CharsetUtil.UTF_8)));
        pong.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return pong;
    }
}
