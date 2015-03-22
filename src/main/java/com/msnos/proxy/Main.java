package com.msnos.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Proxy.class);

    public static void main(String[] args) throws Exception {
        CliParams params = new CliParams(args);
        
        final Microcloud nimbus = new Microcloud(new Cloud(params.uuid()));
        log.info("Cloud: {}", nimbus);

        final Microservice myself = new Microservice("Proxy");
        myself.join(nimbus);
        log.info("Userv: {}", myself);

        System.out.println("---------------------------------------------");
        System.out.println("MS/NOS Dynamic Proxy 1.0");
        System.out.println("- port: "+params.port());
        System.out.println("- uuid: "+params.uuid());
        
        Proxy proxy = new Proxy(myself, params.port());
        proxy.start();
        
        if (Boolean.getBoolean("com.msnos.proxy.api.republisher")) {
            System.out.println("Republishing peoxy is also enabled!");
            ProxyApiWatchdog watchdog = new ProxyApiWatchdog(myself, params.port());
            watchdog.start();
            log.info("watchdoh started!");
        }
    }
}