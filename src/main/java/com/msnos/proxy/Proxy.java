package com.msnos.proxy;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Queue;
import java.util.Set;

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
import com.msnos.proxy.filter.passive.PassiveServiceFilter;
import com.workshare.msnos.core.protocols.ip.AddressResolver;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.usvc.Microservice;

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
                .withName("500")
                .withFiltersSource(getAlwaysUnavailableFilter())
                .withTransparent(true);
        
        HttpProxyServerBootstrap main = DefaultHttpProxyServer
                .bootstrap()
                .withName("MAIN")
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .withChainProxyManager(chainedProxyManager())
                .withTransparent(true);

        bindToNetworkInterfaces(redo, main);

        main.start();
        redo.start();
    }

    public void bindToNetworkInterfaces(HttpProxyServerBootstrap redo, HttpProxyServerBootstrap main) throws Exception {
        
        // localhost
        bind(main, InetAddress.getLocalHost(), "main", mainPort);
        bind(redo, InetAddress.getLocalHost(), "final redirect endpoint", redirectPort);

        // all local interfaces 
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        Set<Network> nets = Network.listAll(nics, true, false, new AddressResolver(){
            public Network findPublicIP() throws IOException {
                return null;
            }
        });

        for (Network net : nets) {
            byte[] addr = net.getAddress();
            final InetAddress inetAddress = InetAddress.getByAddress(addr);
            bind(main, inetAddress, "main", mainPort);
            bind(redo, inetAddress, "final redirect endpoint", redirectPort);
        }
    }

    public void bind(HttpProxyServerBootstrap boot, final InetAddress byAddress, String iden, final int port) {
        final InetSocketAddress socketAddr = new InetSocketAddress(byAddress, port);
        boot.withAddress(socketAddr);
        log.info("Binding address {} to {}", socketAddr, iden);
    }

    private HttpFiltersSourceAdapter getHttpFiltersSourceAdapter() {
        return new HttpFiltersSourceAdapter() {

            @Override
            public HttpFilters filterRequest(HttpRequest request) {
                final String uri = request.getUri();
                if (log.isDebugEnabled()) log.debug("Request for uri {}", uri);
                
                if (uri.startsWith("/admin")) {
                    return new AdminFilter(request, microservice);
                } else if (uri.startsWith("/msnos/")) {
                    return new PassiveServiceFilter(request, microservice.getCloud());
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


