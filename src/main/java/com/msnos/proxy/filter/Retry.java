package com.msnos.proxy.filter;

import io.netty.handler.codec.http.HttpResponse;

/**
 * Created by rhys on 19/09/14.
 */
public interface Retry {
    boolean isNeeded(HttpResponse response);
}
