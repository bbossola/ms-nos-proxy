package com.msnos.proxy;

import com.msnos.proxy.filter.AdminFilter;
import com.msnos.proxy.filter.HttpProxyFilter;
import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.net.UnknownHostException;

public class Proxy {

    private final Microservice microservice;

    public Proxy(Microservice microservice) {
        this.microservice = microservice;
    }

    public HttpProxyServer start(int port) throws UnknownHostException {
        return DefaultHttpProxyServer
                .bootstrap()
                .withPort(port)
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .start();
    }

    private HttpFiltersSourceAdapter getHttpFiltersSourceAdapter() {
        return new HttpFiltersSourceAdapter() {
            public HttpFilters filterRequest(HttpRequest request) {
                if (request.getUri().contains("admin/ping") || request.getUri().contains("admin/routes")) {
                    return new AdminFilter(request, microservice);
                } else {
                    return new HttpProxyFilter(request, microservice);
                }
            }
        };
    }
}