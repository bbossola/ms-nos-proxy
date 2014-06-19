package com.msnos.proxy.filter;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class HttpProxyFilterTest {

    private HttpRequest defaultHttpRequest;
    private Microservice microservice;
    private Cloud cloud;
    private HttpProxyFilter filter;

    @Before
    public void setUp() throws Exception {
        cloud = mock(Cloud.class);
        defaultHttpRequest = httpRequest("/service", "/path");
        filter = null;
    }

    @Test
    public void shouldInvokeSearchWithCorrectParameters() throws Exception {
        microservice = getMockMicroserviceWithRestApi("service", "path", 1111, "10.10.20.13/25");

        filter().requestPre(defaultHttpRequest);

        verifyCorrectMicroserivceSearch(microservice, "service", "path");
    }


    @Test
    public void shouldPopulateCorrectlyTheRequestURI() throws Exception {
        microservice = getMockMicroserviceWithRestApi("name", "path", 1111, "10.10.20.13/25");

        filter().requestPre(defaultHttpRequest);

        assertEquals("http://10.10.20.13:1111/name/path/", defaultHttpRequest.getUri());
    }

    @Test
    public void shouldAddCookieWhenWhenRestHasSessionAffinity() throws Exception {
        microservice = mock(Microservice.class);
        RestApi api = getRestApiWithAffinityPutInMicroserivceSearch("service", "path");

        filter().requestPre(defaultHttpRequest);
        HttpResponse actual = (HttpResponse) filter.responsePre(validHttpResponse());

        assertEquals(expectedCookie(api), getHeaders(actual).get(SET_COOKIE));
    }

    @Test
    public void shouldInvokeSearchByIDWhenCookiePresent() throws Exception {
        addHeadersToRequest(defaultHttpRequest, COOKIE, encodeCookie("x-/service/path", Integer.toString(1)));
        microservice = getMockMicroserviceWithIDRestApi("service", "path", "10.10.2.1/25", 1);

        filter().requestPre(defaultHttpRequest);

        verify(microservice).searchApiById(anyLong());
    }

    @Test
    public void shouldReturnCorrectAPIWhenMultipleAffinityHeldInCookie() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.10.2.1/25");
        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1/123");
        defaultHttpRequest = createRequestWithMultipleCookieValues("/service", "/path", "/other", "/diff", getRestApiId(remote));

        filter().requestPre(defaultHttpRequest);

        String url = getRestApiUrl(remote);
        assertEquals(url, defaultHttpRequest.getUri());
    }

    @Test
    public void shouldReturn404WhenSearchesReturnNull() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1/123");
        HttpResponse response = filter().requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

    @Test
    public void should302WhenAffiniteRestApiIsFaulty() throws Exception {
        defaultHttpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:9999/service/path");
        microservice = createLocalMicroserviceAndJoinCloud();

        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1/123");
        addHeadersToRequest(defaultHttpRequest, COOKIE, encodeCookie("x-/service/path", Long.toString(getRestApiId(remote))));
        makeApiFaulty(remote);

        HttpResponse response = filter().requestPre(defaultHttpRequest);

        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
        assertTrue(getHeaders(response).contains(LOCATION));
        assertEquals(defaultHttpRequest.getUri(), getHeaders(response).get(LOCATION));
        assertFalse(defaultHttpRequest.headers().contains(encodeCookie("x-service/path", Long.toString(getRestApiId(remote)))));
    }

    @Test
    public void should302On500ResponseFromAnyMicroservice() throws Exception {
        microservice = mock(Microservice.class);

        filter().requestPre(defaultHttpRequest);
        HttpResponse response = (HttpResponse) filter.responsePre(failedHttpResponse());

        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
        assertTrue(getHeaders(response).contains(LOCATION));
        assertEquals(defaultHttpRequest.getUri(), getHeaders(response).get(LOCATION));
    }

    @Test
    public void shouldMarkRestApiTempFaultyAndRedirectWhen500() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1/123");

        filter().requestPre(defaultHttpRequest);
        HttpResponse response = (HttpResponse) filter().responsePre(failedHttpResponse());

        assertEquals(1, getRestApi(remote).getTempFaults());
        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
    }

    @Test
    public void shouldNOTServeClientApiMarkedAsHealthCheck() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithHealthCheckStatus("service", "path", "11.14.2.1/123");
        HttpResponse response = filter().requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

//    @Test
//    public void shouldReturn500WhenNORestApiAvailable() throws Exception {
//        microservice = createLocalMicroserviceAndJoinCloud();
//
//        HttpResponse response = (HttpResponse) filter().responsePre(filter().requestPre(defaultHttpRequest));
//
//        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.getStatus());
//    }

    private RemoteMicroservice setupRemoteMicroserviceWithHealthCheckStatus(String name, String endpoint, String host) {
        RemoteAgent agent = newRemoteAgent();
        RestApi restApi = new RestApi(name, endpoint, 9999).onHost(host).asHealthCheck();
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        return addRemoteAgentToCloudListAndMicroserviceToLocalList(name, remote, restApi);
    }

    private String encodeCookie(String name, String value) {
        return ServerCookieEncoder.encode(name, value);
    }

    private void addHeadersToRequest(HttpRequest request, String name, String value) {
        request.headers().add(name, value);
    }

    private RestApi makeApiFaulty(RemoteMicroservice remote) {
        RestApi api = getRestApi(remote);
        api.markFaulty();
        return api;
    }

    private HttpHeaders getHeaders(HttpResponse response) {
        return response.headers();
    }

    private long getRestApiId(RemoteMicroservice remote) {
        return getRestApi(remote).getId();
    }

    private String getRestApiUrl(RemoteMicroservice remote) {
        return getRestApi(remote).getUrl();
    }

    private RestApi getRestApi(RemoteMicroservice remote) {
        return remote.getApis().iterator().next();
    }

    private Microservice createLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(cloud);
        return ms;
    }

    private DefaultHttpRequest createRequestWithMultipleCookieValues(String name, String path, String otherName, String otherPath, long restApiId) {
        DefaultHttpRequest request = httpRequest(name, path);
        Cookie cookieOne = new DefaultCookie(String.format("x-%s%s", name, path), Long.toString(restApiId));
        Cookie cookieTwo = new DefaultCookie(String.format("x-%s%s", otherName, otherPath), Integer.toString(1));
        addHeadersToRequest(request, COOKIE, ClientCookieEncoder.encode(cookieOne, cookieTwo));
        return request;
    }

    private RestApi verifyCorrectMicroserivceSearch(Microservice microservice, String service, String path) throws Exception {
        return Mockito.verify(microservice).searchApi(service, path);
    }

    private String expectedCookie(RestApi api) {
        return String.format("x-%s/%s=%s; Path=/", api.getName(), api.getPath(), api.getId());
    }

    private DefaultFullHttpResponse validHttpResponse() {
        return makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    private DefaultFullHttpResponse failedHttpResponse() {
        return makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private DefaultFullHttpResponse makeHttpResponse(HttpVersion version, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status);
    }

    private DefaultHttpRequest httpRequest(String name, String path) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, name + path);
    }

    private HttpProxyFilter setupHttpProxyFilter(HttpRequest httpRequest, Microservice microservice) {
        return new HttpProxyFilter(httpRequest, microservice);
    }

    private RemoteMicroservice setupRemoteMicroserviceWithAffinity(String name, String endpoint, String host) {
        RemoteAgent agent = newRemoteAgent();
        RestApi restApi = new RestApi(name, endpoint, 9999).onHost(host).withAffinity();
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        return addRemoteAgentToCloudListAndMicroserviceToLocalList(name, remote, restApi);
    }

    private RemoteMicroservice addRemoteAgentToCloudListAndMicroserviceToLocalList(String name, RemoteMicroservice remote, RestApi... restApi) {
        putRemoteAgentInCloudAgentsList(remote.getAgent());
        simulateMessageFromCloud(new Message(Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden(), 2, false, new QnePayload(name, restApi)));
        return remote;
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

    private void putRemoteAgentInCloudAgentsList(RemoteAgent agent) {
        Mockito.when(cloud.getRemoteAgents()).thenReturn(new HashSet<RemoteAgent>(Arrays.asList(agent)));
    }

    private Microservice getMockMicroserviceWithIDRestApi(String name, String path, String host, int port) throws Exception {
        Microservice microservice = mock(Microservice.class);
        RestApi api = new RestApi(name, path, port, host).withAffinity();
        Mockito.when(microservice.searchApiById(anyLong())).thenReturn(api);
        return microservice;
    }

    private Microservice getMockMicroserviceWithRestApi(String name, String path, int host, String port) throws Exception {
        Microservice microservice = mock(Microservice.class);
        RestApi api = new RestApi(name, path, host, port);
        Mockito.when(microservice.searchApi(anyString(), anyString())).thenReturn(api);
        return microservice;
    }

    private RestApi getRestApiWithAffinityPutInMicroserivceSearch(String name, String path) throws Exception {
        RestApi api = new RestApi(name, path, 1111, "10.10.10.10/25").withAffinity();
        Mockito.when(microservice.searchApi(anyString(), anyString())).thenReturn(api);
        return api;
    }

    private Set<RestApi> toSet(RestApi... restApi) {
        return new HashSet<RestApi>(Arrays.asList(restApi));
    }

    private RemoteAgent newRemoteAgent() {
        return newRemoteAgent(UUID.randomUUID());
    }

    private HttpFilters filter() {
        if (filter == null)
            filter = setupHttpProxyFilter(defaultHttpRequest, microservice);

        return filter;
    }
}
