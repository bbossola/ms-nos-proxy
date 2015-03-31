package com.msnos.proxy;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Queue;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msnos.proxy.filter.admin.AdminFilter;
import com.msnos.proxy.filter.http.HttpProxyFilter;
import com.msnos.proxy.filter.msnos.MsnosFilter;
import com.msnos.proxy.filter.msnos.PassiveServiceFilter;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;
import com.workshare.msnos.usvc.api.RestApi.Type;

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

    public void start() throws Exception {
        HttpProxyServerBootstrap redo = DefaultHttpProxyServer
                .bootstrap()
                .withPort(redirectPort)
                .withName("500")
                .withFiltersSource(getAlwaysUnavailableFilter())
                .withTransparent(true)
                .withAllowLocalOnly(false);
        
        HttpProxyServerBootstrap main = DefaultHttpProxyServer
                .bootstrap()
                .withPort(mainPort)
                .withName("MAIN")
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .withChainProxyManager(chainedProxyManager())
                .withTransparent(true)
                .withAllowLocalOnly(false);

        main.start();
        redo.start();
        
        microservice.publish(new RestApi("/msnos", mainPort, null, Type.MSNOS_HTTP, false));
    }

    private HttpFiltersSourceAdapter getHttpFiltersSourceAdapter() {
        return new HttpFiltersSourceAdapter() {


            @Override
            public HttpFilters filterRequest(HttpRequest request, ChannelHandlerContext context) {
                final String uri = request.getUri();
                if (log.isDebugEnabled()) log.debug("Request for uri {}", uri);
                
                if (uri.startsWith("/admin")) {
                    return new AdminFilter(request, microservice);
                } else if (uri.startsWith("/msnos")) {
                    return new MsnosFilter(request, microservice.getCloud());
                } else if (uri.startsWith("/pasv/")) {
                    return new PassiveServiceFilter(request, microservice.getCloud());
                } else {
                    return new HttpProxyFilter(request, context, microservice);
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


