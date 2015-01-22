package com.msnos.proxy.filter.admin;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;

import java.util.List;

import org.littleshoot.proxy.HttpFiltersAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.routing.ApiRepository;

public class AdminFilter extends HttpFiltersAdapter {
    private final HttpRequest request;
    private final Microcloud microcloud;

    private final ThreadLocal<Gson> gson = new ThreadLocal<Gson>() {
        @Override
        protected Gson initialValue() {
            return new GsonBuilder().setPrettyPrinting().create();
        }
    };

    public AdminFilter(HttpRequest request, Microservice microservice) {
        super(request);
        this.request = request;
        this.microcloud = microservice.getCloud();
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            if (request.getUri().contains("admin/microservices")) response = microservices();
            if (request.getUri().contains("admin/routes")) response = routes();
            if (request.getUri().contains("admin/ping")) response = pong();
        }
        return response != null ? response : new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
    }
                
                
    private HttpResponse microservices() {
        List<RemoteMicroservice> micros = microcloud.getMicroServices();
        String content = gson.get().toJson(micros);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(content));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return resp;
    }


    private HttpResponse routes() {
        ApiRepository apis = microcloud.getApis();
        String content = gson.get().toJson(apis);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(content));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return resp;
    }

    private HttpResponse pong() {
        DefaultFullHttpResponse pong = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent("pong"));
        pong.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return pong;
    }

    private ByteBuf writeContent(String resp) {
        return Unpooled.buffer(resp.length()).writeBytes(resp.getBytes(CharsetUtil.UTF_8));
    }

    
}
