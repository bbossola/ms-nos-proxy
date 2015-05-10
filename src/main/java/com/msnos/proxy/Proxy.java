package com.msnos.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Queue;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFilters;
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
    private final CliParams params;

    public Proxy(Microservice microservice, CliParams params) {
        this.microservice = microservice;
        this.params = params;
    }

    public void start() throws Exception {
        HttpProxyServerBootstrap main = DefaultHttpProxyServer
                .bootstrap()
                .withPort(params.port())
                .withIdleConnectionTimeout(params.idleTimeoutInSeconds())
                .withConnectTimeout(params.connectTimeoutInSeconds()*1000)
                .withName("MAIN")
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .withChainProxyManager(chainedProxyManager())
                .withTransparent(true)
                .withAllowLocalOnly(false);

        main.start();
        
        microservice.publish(new RestApi("/msnos", params.port(), null, Type.MSNOS_HTTP, false));
    }

    protected ChainedProxyManager chainedProxyManager() {
        return new ChainedProxyManager() {
            @Override
            public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
                chainedProxies.add(defaultProxy());
                chainedProxies.add(useIfConnectionFailedProxy());
            }
            
            private ChainedProxyAdapter useIfConnectionFailedProxy() {
                return new ChainedProxyAdapter() {

                    @Override
                    public InetSocketAddress getChainedProxyAddress() {
                        try {
                            return new InetSocketAddress(InetAddress.getByName("127.0.0.1"), params.port());
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
        };
    }
}


