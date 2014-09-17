package com.msnos.proxy;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MsnosException;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyApiWatchdog {

    private static final Logger log = LoggerFactory.getLogger(ProxyApiWatchdog.class);

    private final Cloud cloud;
    private final Microservice microservice;
    private final Map<String, RestApi> proxiedServices;

    public ProxyApiWatchdog(Cloud cloud, Microservice microservice) {
        this.cloud = cloud;
        this.microservice = microservice;

        this.proxiedServices = new ConcurrentHashMap<String, RestApi>();
    }


    public void start() {
        cloud.addListener(new Cloud.Listener() {
            @Override
            public void onMessage(Message message) {
                if (message.getType() == Message.Type.QNE) {
                    QnePayload qnePayload = (QnePayload) message.getData();

                    Set<RestApi> apis = qnePayload.getApis();
                    List<RestApi> toPublish = createProxyRestApis(apis);

                    try {
                        if (!toPublish.isEmpty()) {
                            microservice.publish(toPublish.toArray(new RestApi[toPublish.size()]));
                        }
                    } catch (MsnosException e) {
                        log.error("Unable to publish reverse proxied api ", e);
                    }

                    for (RestApi rest : apis) {
                        if (!proxiedServices.containsKey(rest.getUrl()))
                            proxiedServices.put(rest.getUrl(), rest);
                    }
                }
            }
        });
    }

    private List<RestApi> createProxyRestApis(Set<RestApi> apis) {
        List<RestApi> toPublish = new ArrayList<RestApi>();
        for (RestApi api : apis) {
            if (!proxiedServices.containsKey(api.getUrl()) && api.getType() != RestApi.Type.HEALTHCHECK)
                toPublish.add(api.onPort(Integer.getInteger("proxy.port", 8881)));
        }
        return toPublish;
    }
}
