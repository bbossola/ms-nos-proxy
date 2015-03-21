package com.msnos.proxy.filter;

import io.netty.handler.codec.http.HttpResponse;

public class HttpRetry implements Retry {

    @Override
    public boolean isNeeded(HttpResponse response) {
        final int statusCode = response.getStatus().code();
        if (statusCode < 300)
            return false;
        else if (statusCode >= 500)
            return true;
        else
            return isErrorRecoverable(response.getStatus().code());
    }

    private boolean isErrorRecoverable(final int statusCode) {
        boolean result;
        switch (statusCode) {
            case 404:
                result = true;
                break;
            case 408:
                result = true;
                break;
            case 409:
                result = true;
                break;
            case 429:
                result = true;
                break;
            case 449:
                result = true;
                break;
            case 451:
                result = true;
                break;
            default:
                result = false;
                break;
        }
        return result;
    }
}
