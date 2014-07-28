package com.msnos.proxy.filter;

import com.workshare.msnos.core.*;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class HttpRouterTest {

    private Cloud cloud;

    @Before
    public void setUp() throws Exception {
        cloud = mock(Cloud.class);
        Iden iden = new Iden(Iden.Type.CLD, UUID.randomUUID());
        when(cloud.getIden()).thenReturn(iden);
    }

    @Test
    public void shouldRemoveServiceFromProxyingWhenFLTReceived() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/service/path");

        Microservice microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.20.10.102");

        HttpRouter router = new HttpRouter(request, microservice);
        simulateMessageFromCloud(new MessageBuilder(Message.Type.FLT, cloud.getIden(), microservice.getAgent().getIden()).with(2).reliable(false).with(new FltPayload(remote.getAgent().getIden())).make());

        router.routeClient(request);
        HttpResponse response = router.serviceResponse(validHttpResponse());

        assertFalse(response.headers().contains(SET_COOKIE));
    }

    @Test
    public void shouldSendFinal500IfNoRestApis() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:8881/service/path");
        Microservice microservice = createLocalMicroserviceAndJoinCloud();

        HttpRouter router = new HttpRouter(request, microservice);

        assertEquals("All endpoints for service/path are momentarily faulty", getBodyTextFromResponse((DefaultFullHttpResponse) router.serviceResponse(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))));
    }

    private DefaultFullHttpResponse validHttpResponse() {
        return makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    private DefaultFullHttpResponse makeHttpResponse(HttpVersion version, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status);
    }

    private String getBodyTextFromResponse(DefaultFullHttpResponse response) {
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < response.content().capacity(); i++) {
            actual.append((char) response.content().getByte(i));
        }
        return actual.toString();
    }

    private Microservice createLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(cloud);
        return ms;
    }

    private RemoteMicroservice setupRemoteMicroserviceWithAffinity(String name, String endpoint, String host) {
        RemoteAgent agent = newRemoteAgent();
        RestApi restApi = new RestApi(name, endpoint, 9999).onHost(host).withAffinity();
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        return addRemoteAgentToCloudListAndMicroserviceToLocalList(name, remote, restApi);
    }

    private RemoteMicroservice addRemoteAgentToCloudListAndMicroserviceToLocalList(String name, RemoteMicroservice remote, RestApi... restApi) {
        putRemoteAgentInCloudAgentsList(remote.getAgent());
        simulateMessageFromCloud(new MessageBuilder(Message.Type.QNE, remote.getAgent(), cloud).with(new QnePayload(name, restApi)).make());
        return remote;
    }

    private void putRemoteAgentInCloudAgentsList(RemoteAgent agent) {
        Mockito.when(cloud.getRemoteAgents()).thenReturn(new HashSet<RemoteAgent>(Arrays.asList(agent)));
    }

    private RemoteAgent newRemoteAgent() {
        return newRemoteAgent(UUID.randomUUID());
    }

    private RemoteAgent newRemoteAgent(final UUID uuid, Network... hosts) {
        RemoteAgent remote = new RemoteAgent(uuid, cloud, new HashSet<Network>(Arrays.asList(hosts)));
        putRemoteAgentInCloudAgentsList(remote);
        return remote;
    }

    private Message simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        cloudListener.getValue().onMessage(message);
        return message;
    }

    private Set<RestApi> toSet(RestApi... restApi) {
        return new HashSet<RestApi>(Arrays.asList(restApi));
    }
}


