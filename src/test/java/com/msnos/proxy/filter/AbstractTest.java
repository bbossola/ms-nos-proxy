package com.msnos.proxy.filter;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public abstract class AbstractTest {

    protected Cloud cloud;

    public void prepare() throws Exception {
        cloud = mock(Cloud.class);
        Iden iden = new Iden(Iden.Type.CLD, UUID.randomUUID());
        when(cloud.getIden()).thenReturn(iden);
    }

    protected Message newQNEMessage(String name, RemoteMicroservice remote, RestApi... restApi) {
        return new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden())
            .with(UUID.randomUUID())
            .with(new QnePayload(name, restApi)).make();
    }

    protected RemoteAgent newRemoteAgent() {
        return newRemoteAgent(UUID.randomUUID());
    }

    protected RemoteMicroservice addRemoteAgentToCloudListAndMicroserviceToLocalList(String name, RemoteMicroservice remote, RestApi... restApi) {
        putRemoteAgentInCloudAgentsList(remote.getAgent());
        simulateMessageFromCloud(this.newQNEMessage(name, remote, restApi));
        return remote;
    }

    protected RemoteAgent newRemoteAgent(final UUID uuid, Endpoint... endpoints) {
        RemoteAgent remote = new RemoteAgent(uuid, cloud, new HashSet<Endpoint>(Arrays.asList(endpoints)));
        putRemoteAgentInCloudAgentsList(remote);
        return remote;
    }

    protected void putRemoteAgentInCloudAgentsList(RemoteAgent agent) {
        Mockito.when(cloud.getRemoteAgents()).thenReturn(new HashSet<RemoteAgent>(Arrays.asList(agent)));
    }

    protected Message simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        cloudListener.getValue().onMessage(message);
        return message;
    }

    protected RemoteMicroservice setupRemoteMicroserviceWithAffinity(String name, String endpoint, String host) {
        RemoteAgent agent = newRemoteAgent();
        RestApi restApi = new RestApi(name, endpoint, 9999).onHost(host).withAffinity();
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        return addRemoteAgentToCloudListAndMicroserviceToLocalList(name, remote, restApi);
    }

    protected Set<RestApi> toSet(RestApi... restApi) {
        return new HashSet<RestApi>(Arrays.asList(restApi));
    }
    
    protected DefaultFullHttpResponse validHttpResponse() {
        return makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    protected DefaultFullHttpResponse makeHttpResponse(HttpVersion version, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status);
    }

   
}
