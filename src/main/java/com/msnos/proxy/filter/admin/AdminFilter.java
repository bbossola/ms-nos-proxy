package com.msnos.proxy.filter.admin;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.littleshoot.proxy.HttpFiltersAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Ring;
import com.workshare.msnos.core.geo.Location;
import com.workshare.msnos.usvc.IMicroService;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;
import com.workshare.msnos.usvc.api.routing.ApiEndpoint;
import com.workshare.msnos.usvc.api.routing.ApiList;

public class AdminFilter extends HttpFiltersAdapter {
    
    private static final String PATH_ADMIN_PING = "admin/ping";
    private static final String PATH_ADMIN_ROUTES = "admin/routes";
    private static final String PATH_ADMIN_RINGS = "admin/rings";
    private static final String PATH_ADMIN_MICROSERVICES = "admin/microservices";
    
    private final Microcloud microcloud;
    private final HttpRequest request;
    private final Microservice microservice;
    
    private final ThreadLocal<Gson> gson = new ThreadLocal<Gson>() {
        @Override
        protected Gson initialValue() {
            return new GsonBuilder().setPrettyPrinting().create();
        }
    };

    public AdminFilter(HttpRequest request, Microservice microservice) {
        super(request);
        this.request = request;
        this.microservice = microservice;
        this.microcloud = microservice.getCloud();
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof HttpRequest) {
            if (request.getUri().contains(PATH_ADMIN_MICROSERVICES)) response = microservices();
            if (request.getUri().contains(PATH_ADMIN_ROUTES)) response = routes();
            if (request.getUri().contains(PATH_ADMIN_RINGS)) response = rings();
            if (request.getUri().contains(PATH_ADMIN_PING)) response = pong();
        }
        return response != null ? response : new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
    }
                
                
    private HttpResponse microservices() {
        List<RemoteMicroservice> micros = microcloud.getMicroServices();
        String content = gson.get().toJson(micros);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(content));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return resp;
    }

    private HttpResponse routes() {
        String argument = getArgument(PATH_ADMIN_ROUTES);
        
        Map<String, List<JsonObject>> result = new HashMap<String, List<JsonObject>>();
        Map<String, ApiList> apis = microcloud.getApis().getRemoteApis();
        for (String path : apis.keySet()) {
            final ApiList list = apis.get(path);
            if (!path.contains(argument))
                continue;

            final List<JsonObject> entries = new ArrayList<JsonObject>();
            result.put(path, entries);

            for (ApiEndpoint endpoint : list.getEndpoints()) {
                final RestApi api = endpoint.api();
                final Location loc = endpoint.location();
                final RemoteMicroservice svc = endpoint.service();

                JsonObject entry = new JsonObject();
                entry.addProperty("service", svc.getName());
                entry.addProperty("url", api.getUrl());
                entry.addProperty("type", api.getType().toString());
                entry.addProperty("faulty", isFaulty(api));
                entry.addProperty("sticky", api.hasAffinity());
                entry.addProperty("priority", api.getPriority());
                entry.addProperty("location", loc.toString());
                entries.add(entry);
            }
        }
        
        String content = gson.get().toJson(result);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(content));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return resp;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse rings() {
        Cloud cloud = microcloud.getCloud();
        Collection<JsonObject> rings = createRingMap(cloud.getRemoteAgents(), cloud.getLocalAgents());
        String content = gson.get().toJson(rings);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent(content));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return resp;
    }

    private Collection<JsonObject> createRingMap(Collection<? extends Agent>... agents_array) {
        final Set<Agent> all = new HashSet<Agent>();
        for (Collection<? extends Agent> collection : agents_array) {
            for (Agent agent : collection) {
                all.add(agent);
            }
        }
        
        final Map<UUID, JsonObject> rings = new HashMap<UUID, JsonObject>();
        for (Agent agent : all) {
            Ring ring = agent.getRing();
            JsonObject data = rings.get(ring.uuid());
            if (data == null) {
                data = new JsonObject();
                data.addProperty("uuid", ring.uuid().toString());
                data.addProperty("location", ring.location().toString());
                data.add("agents", new JsonArray());
                rings.put(ring.uuid(), data);
            }

            JsonObject friend = new JsonObject();
            friend.addProperty("agent", agent.getIden().getUUID().toString());
            final IMicroService uservice = findMicroserviceName(agent);
            if (uservice != null) {
                friend.addProperty("uservice", uservice.getName());
                friend.addProperty("location", uservice.getLocation().toString());
            } else {
                friend.addProperty("uservice", "n/a");
            }
            
            JsonArray friends = (JsonArray) data.get("agents");
            friends.add(friend);
        }
        
        return rings.values();
    }


    private IMicroService findMicroserviceName(Agent agent) {
        if (microservice.getAgent().equals(agent)) {
            return microservice;
        }

        List<RemoteMicroservice> services = microcloud.getMicroServices();
        for (RemoteMicroservice service : services) {
            if (service.getAgent().equals(agent)) {
                return service;
            }
        }

        return null;
    }
    private String getArgument(String path) {
        try {
            final String uri = request.getUri().toLowerCase();
            final String argument = uri.substring(uri.indexOf(path) + path.length()+1);
            return argument;
        } catch (Exception whatever) {
            return "";
        }
    }

    private String isFaulty(RestApi api) {
        StringBuffer sb = new StringBuffer();
        if (api.isFaulty() || api.getTempFaults() > 0) {
            sb.append("yes");
            if (api.getTempFaults() > 0) {
                sb.append("(temporary: ");
                sb.append(api.getTempFaults());
                sb.append(")");
            }
                
        } else
            sb.append("no");

        return sb.toString();
    }

    private HttpResponse pong() {
        DefaultFullHttpResponse pong = new DefaultFullHttpResponse(HTTP_1_1, OK, writeContent("pong"));
        pong.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        return pong;
    }

    private ByteBuf writeContent(String resp) {
        return Unpooled.buffer(resp.length()).writeBytes(resp.getBytes(CharsetUtil.UTF_8));
    }

    
}
