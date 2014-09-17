package com.msnos.proxy;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;

import java.util.UUID;

public class Main {

    private static int port = Integer.getInteger("proxy.port", 8881);

    public static void main(String[] args) throws Exception {
        String name = "com.msnos.proxy.Proxy";

        Microservice microservice = new Microservice(name);
        Cloud nimbus = new Cloud(new UUID(111, 222));

        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(nimbus, microservice);
        watchdog.start();

        microservice.join(nimbus);

        RestApi restApi = new RestApi(name, "test", port);
        microservice.publish(restApi);

        Proxy proxy = new Proxy(microservice, port);
        proxy.start();
    }
}