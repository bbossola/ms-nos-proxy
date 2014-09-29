package com.msnos.proxy;

import com.msnos.proxy.filter.admin.AdminFilter;
import com.msnos.proxy.filter.http.HttpProxyFilter;
import com.msnos.proxy.filter.passive.PassiveServiceFilter;
import com.workshare.msnos.usvc.Microservice;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class Proxy {

    private static final Logger log = LoggerFactory.getLogger(Proxy.class);

    private final Microservice microservice;
    private final int mainPort;
    private final int redirectPort;

    public Proxy(Microservice microservice, int port) {
        this.microservice = microservice;
        this.mainPort = port;
        this.redirectPort = mainPort + 1;
    }

    public void start() throws UnknownHostException {
        DefaultHttpProxyServer
                .bootstrap()
                .withName("500")
                .withPort(redirectPort)
                .withFiltersSource(getAlwaysUnavailableFilter())
                .withTransparent(true)
                .start();

        DefaultHttpProxyServer
                .bootstrap()
                .withName("MAIN")
                .withPort(mainPort)
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .withChainProxyManager(chainedProxyManager())
                .withTransparent(true)
                .start();
    }

    private HttpFiltersSourceAdapter getHttpFiltersSourceAdapter() {
        return new HttpFiltersSourceAdapter() {

            @Override
            public HttpFilters filterRequest(HttpRequest request) {
                if (request.getUri().startsWith("/admin")) {
                    return new AdminFilter(request, microservice);
                } else if (request.getUri().startsWith("/msnos/")) {
                    return new PassiveServiceFilter(request, microservice);
                } else {
                    return new HttpProxyFilter(request, microservice);
                }
            }

            @Override
            public int getMaximumRequestBufferSizeInBytes() {
                return 2048;
            }
        };
    }

    protected ChainedProxyManager chainedProxyManager() {
        return new ChainedProxyManager() {
            @Override
            public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
                chainedProxies.add(defaultProxy());
                chainedProxies.add(notfoundProxy());
            }

            private ChainedProxyAdapter notfoundProxy() {
                return new ChainedProxyAdapter() {
                    @Override
                    public InetSocketAddress getChainedProxyAddress() {
                        try {
                            return new InetSocketAddress(InetAddress.getByName("127.0.0.1"), redirectPort);
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            }

            private ChainedProxy defaultProxy() {
                return ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION;
            }
        };
    }

    private HttpFiltersSource getAlwaysUnavailableFilter() {
        return new HttpFiltersSourceAdapter() {
            public HttpFilters filterRequest(HttpRequest request) {
                return new HttpFiltersAdapter(request) {
                    @Override
                    public HttpResponse requestPre(HttpObject httpObject) {
                        log.debug("Helo, this is the 500 proxy server returning server error :)");
                        return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
                    }
                };
            }
        };
    }
}


