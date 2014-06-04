import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;

public class Proxy {

    Logger log = LoggerFactory.getLogger(Proxy.class);

    private final Microservice microservice;

    public Proxy(Microservice microservice) {
        this.microservice = microservice;
    }

    public HttpProxyServer start(int port) throws UnknownHostException {
        return DefaultHttpProxyServer
                .bootstrap()
                .withPort(port)
                .withFiltersSource(getHttpFiltersSourceAdapter())
                .start();
    }

    private HttpFiltersSourceAdapter getHttpFiltersSourceAdapter() {
        return new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {

                    @Override
                    public HttpResponse requestPre(HttpObject httpObject) {
                        if (httpObject instanceof HttpRequest) {
                            HttpRequest httpRequest = (HttpRequest) httpObject;
                            try {
                                httpRequest.setUri(routeRequest(httpRequest));
                                httpRequest.headers().set(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.encode("Content", "Some"));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        return super.requestPre(httpObject);
                    }
                };
            }
        };
    }

    private String routeRequest(HttpRequest httpRequest) throws Exception {
        String uri = httpRequest.getUri().replaceAll("(http://|http://www\\.|www\\.)", "");
        String[] request = uri.split("/");
        RestApi rest = microservice.searchApi(request[1], request[2]);
        return rest.getHost().substring(0, rest.getHost().indexOf("/")) + ":" + rest.getPort();
    }
}