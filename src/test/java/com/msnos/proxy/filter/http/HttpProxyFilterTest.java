package com.msnos.proxy.filter.http;

import static com.msnos.proxy.filter.http.HttpRouter.COOKIE_PREFIX;
import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.msnos.proxy.TestHelper.*;

import java.awt.List;
import java.util.UUID;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;

import org.junit.Before;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;

import com.msnos.proxy.TestHelper;
import com.msnos.proxy.filter.AbstractTest;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.usvc.Microcloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.RestApi;
import com.workshare.msnos.usvc.api.routing.ApiList;

@SuppressWarnings("unused")
public class HttpProxyFilterTest {

    private static final String SERVICE = "service";
    private static final String PATH = "/path";
    private static final String HOST = "10.10.10.1";
    
    private Microcloud microcloud;
    private Microservice microservice;

    private HttpProxyFilter filter;
    private HttpRequest request;
    
    private ApiList apis;
    
    @Before
    public void prepare() throws Exception {
        
        request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, PATH);
        filter = null;
        
        microcloud = mock(Microcloud.class);
        microservice = createMockMicroservice();

        apis = new ApiList();
    }

    @Test
    public void shouldInvokeSearchWithCorrectParameters() throws Exception {

        invoke();
        
        verify(microservice).searchApi(PATH);
    }

    @Test
    public void shouldPopulateRequestURIWithTheApiFullUrl() throws Exception {
        RestApi api = new RestApi(SERVICE, PATH, 1111);
        installApi(PATH, api);

        invoke();

        assertEquals(api.getUrl(), request.getUri());
    }

    @Test
    public void shouldReturn404WhenSearchesReturnsNull() throws Exception {
        installApi("/foo");
        
        HttpResponse response = invoke();

        assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());
    }

    
    @Test
    public void shouldAddCookieWhenWhenApiHasSessionAffinity() throws Exception {
        RestApi api = new RestApi(SERVICE, PATH, 1111, HOST).withAffinity();
        installApi(PATH, api);

        HttpResponse response = invoke();
        
        assertEquals(expectedCookie(api), getHeaders(response).get(SET_COOKIE));
    }

    @Test
    public void shouldInvokeSearchByIDWhenCookiePresent() throws Exception {
        final long id = 12345L;
        addHeadersToRequest(request, COOKIE, affinityCookie(id));

        invoke();
    
        verify(microcloud).searchApiById(id);
    }

    @Test
    public void shouldInvokeStandardSearchWhenSearchByCookieFails() throws Exception {
        addHeadersToRequest(request, COOKIE, affinityCookie(Long.MAX_VALUE));

        invoke();
    
        verify(microservice).searchApi(PATH);
    }

    @Test
    public void shouldInvokeStandardSearchWhenFunnyAffinityCookieFound() throws Exception {
        addHeadersToRequest(request, COOKIE, ServerCookieEncoder.encode(COOKIE_PREFIX+PATH, "Boom!"));

        invoke();
    
        verify(microcloud, never()).searchApiById(anyLong());
        verify(microservice).searchApi(PATH);
    }


    @Test
    public void shouldReturnCorrectAPIWhenMultipleAffinityHeldInCookie() throws Exception {
        
        RestApi thisApi = installApi(PATH, new RestApi(SERVICE, PATH, 1111).withAffinity());
        RestApi othrApi = installApi(PATH, new RestApi("other", "/foo", 9999).withAffinity());
        addApisCookie(thisApi, othrApi);

        invoke();

        assertEquals(thisApi.getUrl(), request.getUri());
    }

    @Test
    public void shouldFollowToNextAlternativeAndNoCookieWhenAffiniteRestApiIsFaultyAndAlternativeIsAvailable() throws Exception {

        RestApi api = installApi(PATH, new RestApi(SERVICE, PATH, 1111).withAffinity()).markFaulty();
        RestApi two = installApi(PATH, new RestApi(SERVICE, PATH, 9999));

        addHeadersToRequest(request, COOKIE, affinityCookie(api.getId()));
        HttpResponse response = invoke();

        assertEquals(request.getUri(), two.getUrl());
        assertFalse(response.headers().contains(affinityCookie(api.getId())));
    }
    
    @Test
    public void shouldReturnBadGatewayAndNoCookieWhenAffiniteRestApiIsFaultyAndNoAlternatives() throws Exception {

        RestApi api = installApi(PATH, new RestApi(SERVICE, PATH, 1111).withAffinity());
        api.markFaulty();

        addHeadersToRequest(request, COOKIE, affinityCookie(api.getId()));
        HttpResponse response = invoke();

        assertEquals(HttpResponseStatus.BAD_GATEWAY, response.getStatus());
        assertFalse(request.headers().contains(affinityCookie(api.getId())));
    }

    @Test
    public void shouldReturnRedirectOnApiTemporaryFaultAndNoAlternativesAvailable() throws Exception {
        final RestApi api = installApi(PATH);
        
        HttpResponse response = invoke(internalError());
       
        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
    }

    @Test
    public void shouldReturnBadGatewayWhenApiFaultyAndNoAlternativesAvailable() throws Exception {
        final RestApi api = installApi(PATH);
        api.markFaulty();
        
        HttpResponse response = invoke();
       
        assertEquals(HttpResponseStatus.BAD_GATEWAY, response.getStatus());
    }

    @Test
    public void shouldMarkRestApiTempFaultyAndRedirectWhenReceiving5xxWithAlternatives() throws Exception {
        final RestApi api = installApi(PATH);
        final RestApi two = installApi(PATH);

        HttpResponse response = invoke(internalError());

        assertEquals(1, api.getTempFaults());
    }


    @Test
    public void shouldNOTServeClientApiMarkedAsHealthCheck() throws Exception {
        installApi(PATH, new RestApi(SERVICE, PATH, 1111, HOST, RestApi.Type.HEALTHCHECK, false));

        HttpResponse response = invoke();

        assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());
    }

    @Test
    public void shouldNOTServeClientApiMarkedAsInternal() throws Exception {
        installApi(PATH, new RestApi(SERVICE, PATH, 1111, HOST, RestApi.Type.INTERNAL, false));

        HttpResponse response = invoke();

        assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());
    }

    private RestApi installApi(final String path) {
        return installApi(path, new RestApi(SERVICE, path, 9999));
    }

    private RestApi installApi(final String path, final RestApi api) {
        
        if (apis.size() == 0) {
            Answer<RestApi> answer = new Answer<RestApi>(){
                @Override
                public RestApi answer(InvocationOnMock invocation) throws Throwable {
                    final RestApi restApi = apis.get(microservice);
                    return restApi;
                }};
    
            when(microcloud.canServe(path)).thenReturn(true);
            when(microcloud.searchApi(microservice, path)).thenAnswer(answer);
            when(microcloud.searchApiById(api.getId())).thenAnswer(answer);
            when(microservice.searchApi(path)).thenAnswer(answer);
        }
        
        apis.add(TestHelper.newRemoteMicroservice(), api);
        return api;
    }

    private void assertRedirectTo(HttpResponse response, final String uri) {
        assertEquals(HttpResponseStatus.FOUND, response.getStatus());
        assertEquals(uri, getHeaders(response).get(LOCATION));
    }

    private String affinityCookie(Long value) {
        return ServerCookieEncoder.encode(COOKIE_PREFIX+PATH, value.toString());
    }

    private String encodeCookie(String name, String value) {
        return ServerCookieEncoder.encode(name, value);
    }

    private void addHeadersToRequest(HttpRequest request, String name, String value) {
        request.headers().add(name, value);
    }

    private HttpHeaders getHeaders(HttpResponse response) {
        return response.headers();
    }

    private void addApisCookie(RestApi one, RestApi two) {
        Cookie cookieOne = new DefaultCookie(String.format(COOKIE_PREFIX+one.getPath()), Long.toString(one.getId()));
        Cookie cookieTwo = new DefaultCookie(String.format(COOKIE_PREFIX+two.getPath()), Long.toString(two.getId()));
        addHeadersToRequest(request, COOKIE, ClientCookieEncoder.encode(cookieOne, cookieTwo));
    }

    private String expectedCookie(RestApi api) {
        return String.format(COOKIE_PREFIX+"%s=%s; Path=/", api.getPath(), api.getId());
    }

    private HttpProxyFilter setupHttpProxyFilter(HttpRequest httpRequest) {
        return new HttpProxyFilter(httpRequest, microservice);
    }

    private Microservice createMockMicroservice() {
        Microservice service = mock(Microservice.class);
        when(service.getCloud()).thenReturn(microcloud);
        return service;
    }

    private HttpFilters filter() {
        if (filter == null)
            filter = setupHttpProxyFilter(request);

        return filter;
    }

    private DefaultFullHttpResponse internalError() {
        return makeHttpResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    public HttpResponse invoke() {
        return invoke(success());
    }

    public HttpResponse invoke(DefaultFullHttpResponse apiResponse) {
        HttpResponse response = filter().requestPre(request);
        if (response == null) {
            response = (HttpResponse) filter().responsePre(apiResponse);
        }
        
        return response;
    }
}
