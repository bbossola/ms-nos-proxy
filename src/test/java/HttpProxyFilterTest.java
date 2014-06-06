import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.handler.codec.http.*;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;

public class HttpProxyFilterTest {

    private HttpRequest defaultHttpRequest;
    private Microservice microservice;

    @Test
    public void shouldInvokeSearchWithCorrectParameters() throws Exception {
        defaultHttpRequest = getDefaultHttpRequest("/service", "/path");
        microservice = getMockMicroserviceWithRestApi("service", "path", 1111, "workshare.com/something");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        verifyCorrectMicroserivceSearch(microservice, "service", "path");
    }

    @Test
    public void shouldPopulateCorrectlyTheRequestURI() throws Exception {
        defaultHttpRequest = getDefaultHttpRequest("/service", "/path");
        microservice = getMockMicroserviceWithRestApi("name", "path", 1111, "workshare.com/something");

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        assertEquals("http://workshare.com:1111/name/path/", defaultHttpRequest.getUri());
    }

    @Test
    public void shouldAddCookieWhenWhenRestHasSessionAffinity() throws Exception {
        defaultHttpRequest = getDefaultHttpRequest("/service", "/path");
        microservice = mock(Microservice.class);
        RestApi api = getRestApiWithAffinityPutInMicroserivceSearch();

        HttpProxyFilter filter = setupHttpProxyFilter(defaultHttpRequest, microservice);
        filter.requestPre(defaultHttpRequest);

        HttpResponse actual = (HttpResponse) filter.responsePre(getHttpResponseOK());

        assertEquals(getExpectedCookie(api), actual.headers().get("COOKIE"));
    }

    private RestApi getRestApiWithAffinityPutInMicroserivceSearch() throws Exception {
        RestApi api = new RestApi("name", "path", 1111, "workshare.com/something").withAffinity();
        Mockito.when(microservice.searchApi(anyString(), anyString())).thenReturn(api);
        return api;
    }

    private RestApi verifyCorrectMicroserivceSearch(Microservice microservice, String service, String path) throws Exception {
        return Mockito.verify(microservice).searchApi(service, path);
    }

    private String getExpectedCookie(RestApi api) {
        return "x-affinity=\"" + api.getHost() + "/" + api.getPort() + "\"";
    }

    private DefaultFullHttpResponse getHttpResponseOK() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    private DefaultHttpRequest getDefaultHttpRequest(String name, String path) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, name + path);
    }

    private Microservice getMockMicroserviceWithRestApi(String name, String path, int host, String port) throws Exception {
        Microservice microservice = mock(Microservice.class);
        RestApi api = new RestApi(name, path, host, port);
        Mockito.when(microservice.searchApi(anyString(), anyString())).thenReturn(api);
        return microservice;
    }

    private HttpProxyFilter setupHttpProxyFilter(HttpRequest httpRequest, Microservice microservice) {
        return new HttpProxyFilter(httpRequest, microservice);
    }
}
