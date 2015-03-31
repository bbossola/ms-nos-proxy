package com.msnos.proxy.filter.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.geo.Location;
import com.workshare.msnos.core.geo.LocationFactory;
import com.workshare.msnos.usvc.IMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public class ProxiedMicroserviceTest {
    
    private IMicroservice delegate;
    private LocationFactory locations;
    private ChannelHandlerContext context;
    
    @Before
    public void before() {
        this.delegate = mock(IMicroservice.class);
        this.context = mock(ChannelHandlerContext.class);
        this.locations= mock(LocationFactory.class);
    }
    
    @Test
    public void shouldInvokeDelegateOnGetName() {
        when(delegate.getName()).thenReturn("name");
        assertEquals("name", external().getName());
    }

    @Test
    public void shouldInvokeDelegateOnGetAgent() {
        Agent agent = mock(Agent.class);
        when(delegate.getAgent()).thenReturn(agent);
        
        assertEquals(agent, external().getAgent());
    }

    @Test
    public void shouldInvokeDelegateOnGetApis() {
        Set<RestApi> apis = new HashSet<RestApi>();
        when(delegate.getApis()).thenReturn(apis);
        
        assertEquals(apis, external().getApis());
    }

    @Test
    public void shouldUseIpFromContextWhenAskingLocation() {
        Location location = mock(Location.class);
        when(locations.make("foo")).thenReturn(location );
        mockLocationInContextTo("foo");
        
        assertEquals(location, external().getLocation());
    }

    @Test
    public void shouldLocationBeLazyLoaded(){
        IMicroservice service = external();
        service.getAgent();
        service.getApis();
        service.getApis();

        verifyZeroInteractions(locations);
    }

    private IMicroservice external() {
        return new ProxiedMicroservice(delegate, context, locations);
    }
     
    private void mockLocationInContextTo(String host) {
        Channel channel = mock(Channel.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved(host, 0);
        when(channel.remoteAddress()).thenReturn(address);
        when(context.channel()).thenReturn(channel );
    }
}
