import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;

public class HttpProxyFilter extends HttpFiltersAdapter {

    Logger log = LoggerFactory.getLogger(HttpProxyFilter.class);

    private final Microservice microservice;

    private RestApi rest;

    public HttpProxyFilter(HttpRequest originalRequest, Microservice microservice) {
        super(originalRequest);
        this.microservice = microservice;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            try {
                rest = routeClientRequest(httpRequest);
                httpRequest.setUri(rest.getUrl());
            } catch (Exception e) {
                log.error("Routing exception: {}", e);
            }
        }
        return super.requestPre(httpObject);
    }

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;
            if (rest.hasAffinity()) {
                response.headers().set(COOKIE, ServerCookieEncoder.encode("x-affinity", rest.getHost() + "/" + rest.getPort()));
            }
            return response;
        }
        return httpObject;
    }

    private RestApi routeClientRequest(HttpRequest httpRequest) throws Exception {
        String uri = httpRequest.getUri().replaceAll("(http://|http://www\\.|www\\.)", "");
        String[] request = uri.split("/");
        return microservice.searchApi(request[1], request[2]);
    }
}

