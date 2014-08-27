package com.msnos.proxy.filter;

import io.netty.handler.codec.http.HttpResponse;

class RetryLogic {

    public boolean isWorth(HttpResponse response) {
        boolean result = false;
        String respCode = Integer.toString(response.getStatus().code());
        if (respCode.matches("4[0-9]+")) result = clientErr(response);
        if (respCode.matches("5[0-9][1-9]")) result = true;
        return result;
    }

    private boolean clientErr(HttpResponse response) {
        boolean result;
        switch (response.getStatus().code()) {
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
