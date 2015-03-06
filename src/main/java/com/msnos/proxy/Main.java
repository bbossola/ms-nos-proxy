package com.msnos.proxy;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Proxy.class);

    private static int port = Integer.getInteger("proxy.port", 9998);

    public static void main(String[] args) throws Exception {
        final Microcloud nimbus = new Microcloud(new Cloud(new UUID(111, 222)));

        final Microservice myself = new Microservice("Proxy");
        myself.join(nimbus);

//        RestApi restApi = new RestApi("Proxy", "test", port);
//        myself.publish(restApi);

//        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(microservice);
//        watchdog.start();

        log.info("Starting proxy on port {}", port);
        Proxy proxy = new Proxy(myself, port);
        proxy.start();
    }
}