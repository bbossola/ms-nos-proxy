package com.msnos.proxy;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.MsnosException;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;

import java.util.UUID;

public class Main {

    private static int port = Integer.getInteger("proxy.port", 8881);

    public static void main(String[] args) throws Exception {
        String name = "proxy";

        final Microcloud nimbus = new Microcloud(new Cloud(new UUID(111, 222)));

        final Microservice microservice = new Microservice(name);
        microservice.join(nimbus);
        RestApi restApi = new RestApi(name, "test", port);
        microservice.publish(restApi);

//        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(microservice);
//        watchdog.start();

        Proxy proxy = new Proxy(microservice, port);
        proxy.start();
    }
}