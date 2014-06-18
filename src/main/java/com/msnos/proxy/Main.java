package com.msnos.proxy;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;

import java.util.UUID;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8881;
        String name = "com.msnos.proxy.Proxy";
        Microservice microservice = new Microservice(name);
        Cloud nimbus = new Cloud(new UUID(111, 222));

        microservice.join(nimbus);

        RestApi restApi = new RestApi(name, "test", port);
        microservice.publish(restApi);

        Proxy proxy = new Proxy(microservice);
        proxy.start(port);
    }
}