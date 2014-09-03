package com.msnos.proxy.filter;

import io.netty.handler.codec.http.HttpResponse;

class RetryLogic {

    public boolean isWorth(HttpResponse response) {
        final int code = response.getStatus().code();
        return code > 407;
    }
}
