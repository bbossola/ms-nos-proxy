package com.msnos.proxy.filter;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.msnos.proxy.TestHelper;
import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Cloud.Listener;
import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.MsnosException;
import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.core.Ring;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public abstract class AbstractTest {

    protected static final UUID CLOUD_UUID = UUID.randomUUID();

    protected Cloud cloud;
    protected Microcloud microcloud;

    public void prepare() throws Exception {
        cloud = mock(Cloud.class);
        Iden iden = new Iden(Iden.Type.CLD, CLOUD_UUID);
        when(cloud.getIden()).thenReturn(iden);
        when(cloud.getRing()).thenReturn(Ring.random());

        microcloud = new Microcloud(cloud, Mockito.mock(ScheduledExecutorService.class));
    }

    protected Message newQNEMessage(String name, RemoteMicroservice remote, RestApi... restApi) {
        return new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden())
                .with(CLOUD_UUID)
                .with(new QnePayload(name, restApi)).make();
    }

    protected Message newPRSMessage(RemoteAgent agent) {
        try {
            return new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.PRS, agent.getIden(), cloud.getIden())
                    .with(CLOUD_UUID)
                    .with(new Presence(true, agent)).make();
        } catch (MsnosException e) {
            throw new RuntimeException(e);
        }
    }

    protected RemoteMicroservice newRemoteMicroservice() {
        String name = Long.toString(System.nanoTime());
        return new RemoteMicroservice(name , newRemoteAgent(), TestHelper.toSet());
    }

    protected RemoteMicroservice newRemoteMicroservice(final String name) {
        return new RemoteMicroservice(name, newRemoteAgent(), TestHelper.toSet());
    }

    protected RemoteAgent newRemoteAgent() {
        return newRemoteAgent(CLOUD_UUID);
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
        Mockito.when(cloud.find(agent.getIden())).thenReturn(agent);
    }

    protected Message simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        for (Listener listener : cloudListener.getAllValues()) {
            listener.onMessage(message);
        }

        return message;
    }

    protected RemoteMicroservice setupRemoteMicroserviceWithAffinity(String name, String endpoint, String host) {
        RemoteAgent agent = newRemoteAgent();
        
        RestApi restApi = new RestApi(endpoint, 9999).onHost(host).withAffinity();
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, TestHelper.toSet(restApi));
        simulateMessageFromCloud(this.newQNEMessage(name, remote, new RestApi[] { restApi }));

        return remote;
    }
}
