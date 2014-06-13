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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class HttpProxyFilterTest {

    private HttpRequest defaultHttpRequest;
    private Microservice microservice;
    private Cloud cloud;

    @Before
    public void setUp() throws Exception {
        cloud = mock(Cloud.class);
    }

    @Test
    public void shouldInvokeSearchWithCorrectParameters() throws Exception {
        defaultHttpRequest = getHttpRequest("/service", "/path");
        microservice = getMockMicroserviceWithRestApi("service", "path", 1111, "10.10.20.13/25");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        verifyCorrectMicroserivceSearch(microservice, "service", "path");
    }

    @Test
    public void shouldPopulateCorrectlyTheRequestURI() throws Exception {
        defaultHttpRequest = getHttpRequest("/service", "/path");
        microservice = getMockMicroserviceWithRestApi("name", "path", 1111, "10.10.20.13/25");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        assertEquals("http://10.10.20.13:1111/name/path/", defaultHttpRequest.getUri());
    }

    @Test
    public void shouldAddCookieWhenWhenRestHasSessionAffinity() throws Exception {
        defaultHttpRequest = getHttpRequest("/service", "/path");
        microservice = mock(Microservice.class);
        RestApi api = getRestApiWithAffinityPutInMicroserivceSearch("service", "path");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        HttpResponse actual = (HttpResponse) filter.responsePre(getHttpResponseOK());

        assertEquals(getExpectedCookie(api), actual.headers().get(COOKIE));
    }

    @Test
    public void shouldInvokeSearchByIDWhenCookiePresent() throws Exception {
        defaultHttpRequest = getHttpRequest("/service", "/path");
        defaultHttpRequest.headers().add(COOKIE, ServerCookieEncoder.encode("x-" + "/service" + "/path", Integer.toString(1)));
        microservice = getMockMicroserviceWithIDRestApi("service", "path", "10.10.2.1/25", 1);

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        verify(microservice).searchApiById(anyLong());
    }

    @Test
    public void shouldReturnCorrectAPIWhenMultipleAffinityHeldInCookie() throws Exception {
        defaultHttpRequest = getDefaultHttpRequestWithMultipleCookieValues("/service", "/path", "/other", "/diff");
        microservice = getLocalMicroserviceAndJoinCloud();

        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.10.2.1/25");
        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1/123");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        String url = getMicroserviceRestApiUrl(remote);
        assertEquals(url, defaultHttpRequest.getUri());
    }

    @Test
    public void shouldReturn404WhenSearchesReturnNull() throws Exception {
        defaultHttpRequest = getHttpRequest("/service", "/path");
        microservice = getLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1/123");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        HttpResponse response = filter.requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

    private String getMicroserviceRestApiUrl(RemoteMicroservice remote) {
        return remote.getApis().iterator().next().getUrl();
    }

    private Microservice getLocalMicroserviceAndJoinCloud() throws Exception {
        Microservice ms = new Microservice("local");
        ms.join(cloud);
        return ms;
    }

    private DefaultHttpRequest getHttpRequestWithCookiesMakingNewRequest(String reqName, String reqValue, String cookieName, String cookiePath) {
        DefaultHttpRequest request = getHttpRequest(reqName, reqValue);
        Cookie cookieOne = new DefaultCookie("x-" + cookieName + cookiePath, Integer.toString(2));
        request.headers().add(COOKIE, ClientCookieEncoder.encode(cookieOne));
        return request;
    }

    private DefaultHttpRequest getDefaultHttpRequestWithMultipleCookieValues(String name, String path, String otherName, String otherPath) {
        DefaultHttpRequest request = getHttpRequest(name, path);
        Cookie cookieOne = new DefaultCookie("x-" + name + path, Integer.toString(2));
        Cookie cookieTwo = new DefaultCookie("x-" + otherName + otherPath, Integer.toString(1));
        request.headers().add(COOKIE, ClientCookieEncoder.encode(cookieOne, cookieTwo));
        return request;
    }

    private RestApi verifyCorrectMicroserivceSearch(Microservice microservice, String service, String path) throws Exception {
        return Mockito.verify(microservice).searchApi(service, path);
    }

    private String getExpectedCookie(RestApi api) {
        return String.format("x-%s/%s=%s", api.getName(), api.getPath(), api.getId());
    }

    private DefaultFullHttpResponse getHttpResponseOK() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    private DefaultHttpRequest getHttpRequest(String name, String path) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, name + path);
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
}
