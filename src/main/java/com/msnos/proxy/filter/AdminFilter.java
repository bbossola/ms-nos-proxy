package com.msnos.proxy.filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.workshare.msnos.core.geo.LocationFactory;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;
import org.littleshoot.proxy.HttpFiltersAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class AdminFilter extends HttpFiltersAdapter {
    private final HttpRequest request;
    private final Microservice microservice;

    private final Gson gson;

    public AdminFilter(HttpRequest request, Microservice microservice) {
        super(request);
        this.request = request;
        this.microservice = microservice;

        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            if (request.getUri().contains("admin/microservices")) response = microservices();
            if (request.getUri().contains("admin/routes")) response = routes();
            if (request.getUri().contains("admin/ping")) response = pong();
        }
        return response != null ? response : super.requestPre(httpObject);
    }

    private HttpResponse microservices() {
        List<RemoteMicroservice> routes = microservice.getMicroServices();
        List<Map<String, String>> content = new ArrayList<Map<String, String>>();
        for (RemoteMicroservice route : routes) {
            Map<String, String> data = new HashMap<String, String>();
            data.put("name", route.getName());
            data.put("agent", route.getAgent().getIden().toString());
            data.put("current sequence", route.getAgent().getSeq().toString());
            data.put("location", route.getLocation().toString());
            content.add(data);
        }
        String resp1 = gson.toJson(content);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(resp1));
        resp.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return resp;
    }


    private HttpResponse routes() {
        List<RemoteMicroservice> routes = microservice.getMicroServices();
        List<Map<String, String>> content = new ArrayList<Map<String, String>>();
        for (RemoteMicroservice route : routes) {
            Map<String, String> data = new HashMap<String, String>();
            for (RestApi rest : route.getApis()) {
                if (rest.getType() == RestApi.Type.HEALTHCHECK) continue;
                data.put("name", rest.getName());
                data.put("path", rest.getPath());
                data.put("host", rest.getHost());
                data.put("port", String.valueOf(rest.getPort()));
                data.put("location", LocationFactory.DEFAULT.make(rest.getHost()).toString());
                data.put("sessionAffinity", String.valueOf(rest.hasAffinity()));
            }
            if (data.isEmpty()) continue;
            content.add(data);
        }
        String resp1 = gson.toJson(content);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(resp1));
        resp.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return resp;
    }

    private HttpResponse pong() {
        DefaultFullHttpResponse pong = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent("<h1>Pong</h1>"));
        pong.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return pong;
    }

    private ByteBuf writeContent(String resp) {
        return Unpooled.buffer(resp.length()).writeBytes(resp.getBytes(CharsetUtil.UTF_8));
    }

}
