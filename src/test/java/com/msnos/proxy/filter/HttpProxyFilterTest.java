package com.msnos.proxy.filter;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;

import org.junit.Before;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;
import org.mockito.Mockito;

import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RemoteMicroservice;
import com.workshare.msnos.usvc.api.RestApi;

public class HttpProxyFilterTest extends AbstractTest {

    private HttpRequest defaultHttpRequest;
    private Microservice microservice;
    private HttpProxyFilter filter;

    @Before
    public void prepare() throws Exception {
        super.prepare();

        defaultHttpRequest = httpRequest("/service", "/path");
        filter = null;
    }

    @Test
    public void shouldInvokeSearchWithCorrectParameters() throws Exception {
        microservice = getMockMicroserviceWithRestApi("service", "path", 1111, "10.10.20.13/123");

        filter().requestPre(defaultHttpRequest);

        verifyCorrectMicroserivceSearch(microservice, "service", "path");
    }


    @Test
    public void shouldPopulateCorrectlyTheRequestURI() throws Exception {
        microservice = getMockMicroserviceWithRestApi("service", "path", 1111, "10.10.20.13");

        filter().requestPre(defaultHttpRequest);

        assertEquals("http://10.10.20.13:1111/service/path/", defaultHttpRequest.getUri());
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
        microservice = getMockMicroserviceWithIDRestApi("service", "path", "10.10.2.1/123", 1);

        filter().requestPre(defaultHttpRequest);

        verify(microservice).searchApiById(anyLong());
    }

    @Test
    public void shouldReturnCorrectAPIWhenMultipleAffinityHeldInCookie() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "10.10.2.1/123");
        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1");
        defaultHttpRequest = createRequestWithMultipleCookieValues("/service", "/path", "/other", "/diff", getRestApiId(remote));

        filter().requestPre(defaultHttpRequest);

        String url = getRestApiUrl(remote);
        assertEquals(url, defaultHttpRequest.getUri());
    }

    @Test
    public void shouldReturn404WhenSearchesReturnNull() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithAffinity("other", "diff", "11.14.2.1");
        HttpResponse response = filter().requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

    @Test
    public void should302WhenAffiniteRestApiIsFaulty() throws Exception {
        defaultHttpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://127.0.0.1:9999/service/path");
        microservice = createLocalMicroserviceAndJoinCloud();

        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1");
        addHeadersToRequest(defaultHttpRequest, COOKIE, encodeCookie("x-/service/path", Long.toString(getRestApiId(remote))));
        makeApiFaulty(remote);

        HttpResponse response = filter().requestPre(defaultHttpRequest);

        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
        assertTrue(getHeaders(response).contains(LOCATION));
        assertEquals(defaultHttpRequest.getUri(), getHeaders(response).get(LOCATION));
        assertFalse(defaultHttpRequest.headers().contains(encodeCookie("x-service/path", Long.toString(getRestApiId(remote)))));
    }

    @Test
    public void should302On500ResponseFromAnyMicroserviceWhenAnotherApiAvailable() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();
        setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1");
        setupRemoteMicroserviceWithAffinity("service", "path", "11.222.2.121");

        filter().requestPre(defaultHttpRequest);

        HttpResponse response = (HttpResponse) filter.responsePre(failedHttpResponse());

        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
        assertTrue(getHeaders(response).contains(LOCATION));
        assertEquals(defaultHttpRequest.getUri(), getHeaders(response).get(LOCATION));
    }

    @Test
    public void shouldMarkRestApiTempFaultyAndRedirectWhen500() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();
        RemoteMicroservice remote = setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1");
        setupRemoteMicroserviceWithAffinity("service", "path", "11.14.2.1");

        filter().requestPre(defaultHttpRequest);
        HttpResponse response = (HttpResponse) filter().responsePre(failedHttpResponse());

        assertEquals(1, getRestApi(remote).getTempFaults());
        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
    }

    @Test
    public void shouldNOTServeClientApiMarkedAsHealthCheck() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithApiAs("service", "path", "11.14.2.1", RestApi.Type.HEALTHCHECK);
        HttpResponse response = filter().requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

    @Test
    public void shouldNOTServeClientApiMarkedAsInternal() throws Exception {
        microservice = createLocalMicroserviceAndJoinCloud();

        setupRemoteMicroserviceWithApiAs("service", "path", "11.14.2.1", RestApi.Type.INTERNAL);
        HttpResponse response = filter().requestPre(defaultHttpRequest);

        DefaultFullHttpResponse expected = makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertEquals(expected.toString(), response.toString());
    }

    private RemoteMicroservice setupRemoteMicroserviceWithApiAs(String name, String endpoint, String host, RestApi.Type type) {
        RestApi restApi = new RestApi(name, endpoint, 9999, host, type, false);
        RemoteAgent agent = newRemoteAgent();
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

    private DefaultFullHttpResponse failedHttpResponse() {
        return makeHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private DefaultHttpRequest httpRequest(String name, String path) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, name + path);
    }

    private HttpProxyFilter setupHttpProxyFilter(HttpRequest httpRequest, Microservice microservice) {
        return new HttpProxyFilter(httpRequest, microservice);
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
        RestApi api = new RestApi(name, path, 1111, "10.10.10.10/123").withAffinity();
        Mockito.when(microservice.searchApi(anyString(), anyString())).thenReturn(api);
        return api;
    }
    
    private HttpFilters filter() {
        if (filter == null)
            filter = setupHttpProxyFilter(defaultHttpRequest, microservice);

        return filter;
    }
}
