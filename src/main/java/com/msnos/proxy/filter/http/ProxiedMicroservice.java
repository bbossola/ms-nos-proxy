package com.msnos.proxy.filter.http;

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.geo.Location;
import com.workshare.msnos.core.geo.LocationFactory;
import com.workshare.msnos.usvc.IMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public class ProxiedMicroservice implements IMicroservice {

    private static final Logger log = LoggerFactory.getLogger(ProxiedMicroservice.class);

    private final IMicroservice proxied;
    private final ChannelHandlerContext context;
    private final LocationFactory locations;

    private Location location;

    public ProxiedMicroservice(final IMicroservice delegate, final ChannelHandlerContext context) {
        this(delegate, context, LocationFactory.DEFAULT);
    }

    public ProxiedMicroservice(final IMicroservice delegate, final ChannelHandlerContext context, final LocationFactory locations) {
        this.proxied = delegate;
        this.context = context;
        this.locations = locations;
    }

    public IMicroservice getProxied() {
        return proxied;
    }

    @Override
    public String getName() {
        return proxied.getName();
    }

    @Override
    public Set<RestApi> getApis() {
        return proxied.getApis();
    }

    @Override
    public Location getLocation() {
        synchronized (this) {
            try {
                if (location == null) {
                    final SocketAddress remoteAddress = context.channel().remoteAddress();
                    location = locations.make(((InetSocketAddress) remoteAddress).getHostName());
                }
            } catch (Throwable any) {
                log.info("Unable to detect location for a proxied call: " + any.getMessage());
                location = Location.UNKNOWN;
            }

            return location;
        }
    }

    @Override
    public Agent getAgent() {
        return proxied.getAgent();
    }

}
