package com.msnos.proxy;

import com.workshare.msnos.core.*;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.*;

public class ProxyApiWatchdogTest {

    private Cloud cloud;
    private Microservice microservice;
    private Microcloud microcloud;

    @Before
    public void setUp() throws Exception {
        cloud = mock(Cloud.class);
        when(cloud.getIden()).thenReturn(new Iden(Iden.Type.CLD, UUID.randomUUID()));
        when(cloud.getRing()).thenReturn(Ring.random());
        microcloud = new Microcloud(cloud, mock(ScheduledExecutorService.class));

        microservice = new Microservice("WatchDAWG");
        microservice.join(microcloud);
    }

    @Test
    public void shouldListenToTheCloud() throws Exception {
        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(microservice, 8881);
        watchdog.start();

        verify(cloud, atLeastOnce()).addListener(any(Cloud.Listener.class));
    }

    @Test
    public void shouldRepublishProxyQNEWhenQNEPayloadReceived() throws Exception {
        microservice = getMockMicroservice();

        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(microservice, 8881);
        watchdog.start();

        simulateMessageFromCloud(new MessageBuilder(Message.Type.QNE, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).with(new QnePayload("WatchDAWG", new RestApi("WatchDAWG", "test", 9999))).make());
        simulateMessageFromCloud(new MessageBuilder(Message.Type.PIN, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).make());

        verify(microservice, times(1)).publish(any(RestApi.class));
    }

    @Test
    public void shouldNOTPublishAnythingWhenOtherMessagesReceived() throws Exception {
        microservice = getMockMicroservice();

        ProxyApiWatchdog watchdog = new ProxyApiWatchdog(microservice, 8881);
        watchdog.start();

        simulateMessageFromCloud(new MessageBuilder(Message.Type.PRS, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).make());
        simulateMessageFromCloud(new MessageBuilder(Message.Type.PIN, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).make());

        verify(microservice, never()).publish(any(RestApi.class));
    }

    @Test
    public void shouldNOTRepublishAlreadyReverseProxiedApis() throws Exception {
        microservice = getMockMicroservice();

        ProxyApiWatchdog watchdog = new ProxyApiWatchdog( microservice, 8881);
        watchdog.start();

        simulateMessageFromCloud(new MessageBuilder(Message.Type.QNE, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).with(new QnePayload("WatchDAWG", new RestApi("WatchDAWG", "test", 9999))).make());
        simulateMessageFromCloud(new MessageBuilder(Message.Type.QNE, cloud, microservice.getAgent().getIden()).with(UUID.randomUUID()).with(new QnePayload("WatchDAWG", new RestApi("WatchDAWG", "test", 9999))).make());

        verify(microservice, times(1)).publish(any(RestApi.class));
    }

    private Microservice getMockMicroservice() {
        Microservice microservice = mock(Microservice.class);
        LocalAgent agent = mock(LocalAgent.class);
        when(microservice.getCloud()).thenReturn(microcloud);
        when(microservice.getAgent()).thenReturn(agent);
        when(agent.getIden()).thenReturn(new Iden(Iden.Type.AGT, UUID.randomUUID()));
        return microservice;
    }

    private Message simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        cloudListener.getValue().onMessage(message);
        return message;
    }
}