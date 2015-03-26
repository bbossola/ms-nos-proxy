package com.msnos.proxy.filter.msnos;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;

import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.core.serializers.WireJsonSerializer;
import com.workshare.msnos.usvc.Microcloud;

public class MsnosFilter extends HttpFiltersAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(MsnosFilter.class);
    private static final WireJsonSerializer serializer = new WireJsonSerializer();

    private final Microcloud cloud;

    public MsnosFilter(HttpRequest request, Microcloud microcloud) {
        super(request);
        this.cloud = microcloud;
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        HttpResponse response = null;
        if (httpObject instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) httpObject;
            response = handle(request);
        }
        
        return response != null ? response : new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
    }
                
    private HttpResponse handle(FullHttpRequest request) {
        String content = request.content().toString(CharsetUtil.UTF_8);
        Message message = serializer.fromText(content, Message.class);
        log.debug("Message received: {}", message);
        cloud.process(message, Endpoint.Type.HTTP); 
        return new DefaultFullHttpResponse(HTTP_1_1, OK);
    }
}
