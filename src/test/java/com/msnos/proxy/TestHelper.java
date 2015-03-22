package com.msnos.proxy;

import static org.mockito.Mockito.when;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.mockito.Mockito;

import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.Iden.Type;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public class TestHelper {

    public static UUID newUUID() {
        return UUID.randomUUID();
    }

    public static Iden newIden(final Type type) {
        return new Iden(type, UUID.randomUUID());
    }

    public static DefaultFullHttpResponse makeHttpResponse(HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    }

    public static DefaultFullHttpResponse success() {
        return makeHttpResponse(HttpResponseStatus.OK);
    }

    public static Set<RestApi> toSet(RestApi... restApi) {
        return new HashSet<RestApi>(Arrays.asList(restApi));
    }

    public static RemoteMicroservice newRemoteMicroservice() {
        UUID uuid = newUUID();
        final RemoteMicroservice service = Mockito.mock(RemoteMicroservice.class);
        when(service.getUuid()).thenReturn(uuid);
        return service;
    }
}
