package com.msnos.proxy.filter;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RetryLogicTest {

    private RetryLogic retryLogic;

    @Before
    public void setUp() throws Exception {
        retryLogic = new RetryLogic();
    }

    @Test
    public void shouldReturnTrueOn408() throws Exception {
        assertTrue(retryLogic.isWorth(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT)));
    }

    @Test
    public void shouldReturnFalseOnAny4xxNotSpecified() throws Exception {
        assertFalse(retryLogic.isWorth(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)));
    }

    @Test
    public void shouldReturnTrueOnAll5xx() throws Exception {
        assertTrue(retryLogic.isWorth(makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)));
    }

    private DefaultFullHttpResponse makeHttpResponse(HttpVersion version, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status);
    }
}

