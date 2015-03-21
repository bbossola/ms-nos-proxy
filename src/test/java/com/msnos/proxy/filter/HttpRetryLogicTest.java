package com.msnos.proxy.filter;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpRetryLogicTest {

    private HttpRetry retry;

    @Before
    public void setUp() throws Exception {
        retry = new HttpRetry();
    }

    @Test
    public void shouldReturnFalseOn2xx() throws Exception {
        assertFalse(retry.isNeeded(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
    }

    @Test
    public void shouldReturnFalseOn3xx() throws Exception {
        assertFalse(retry.isNeeded(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY)));
    }

    @Test
    public void shouldReturnTrueOn408() throws Exception {
        assertTrue(retry.isNeeded(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT)));
    }

    @Test
    public void shouldReturnFalseOnAny4xxNotSpecified() throws Exception {
        assertFalse(retry.isNeeded(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)));
    }

    @Test
    public void shouldReturnTrueOn5xx() throws Exception {
        assertTrue(retry.isNeeded(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)));
    }

    private DefaultFullHttpResponse makeHttpResponse(HttpVersion version, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status);
    }
}

