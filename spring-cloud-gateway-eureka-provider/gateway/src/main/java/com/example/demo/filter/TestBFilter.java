package com.example.demo.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class TestBFilter implements Ordered, GlobalFilter {
    Logger logger = LoggerFactory.getLogger(TestBFilter.class);
    @Resource
    DiscoveryClient discoveryClient;
    @Resource
    LoadBalancerClient loadBalancerClient;

    @Autowired
    GatewayProperties gatewayProperties;

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("请求进入");
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        return operationExchange(exchange, chain);
    }

    private Mono<Void> operationExchange(ServerWebExchange exchange, GatewayFilterChain chain) {
        // mediaType
        MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
        // read & modify body
        ServerRequest serverRequest = new DefaultServerRequest(exchange, Arrays.asList(new DecoderHttpMessageReader(StringDecoder.allMimeTypes())));
        Map<String,String> map = new ConcurrentHashMap<>();
        Mono<String> modifiedBody = serverRequest.bodyToMono(String.class)
                .flatMap(body -> {
                    if (MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
                        // 对原先的body进行修改操作
                        String newBody = body;
                        JSONObject object = JSON.parseObject(body);
                        map.put("type",object.getString("type"));
                        map.put("json",body);
                        return Mono.just(newBody);
                    }
                    return Mono.empty();
                });
        BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
        return bodyInserter.insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> {
                    ServerHttpRequest req = exchange.getRequest();
                    Route route = getRoute(map.get("type"));
                    URI eTx = null;
                    ServerHttpRequest newRequest = null;
                    //因为是列子
                    if(map.get("type").equals("default_path_to_httpbinB")){
                        eTx = URI.create(route.getUri().toString()+"/providerB/get");
                        DataBuffer bodyDataBuffer = exchange.getResponse().bufferFactory().wrap(map.get("json").getBytes());
                        Flux<DataBuffer> bodyFlux = Flux.just(bodyDataBuffer);
                        newRequest = req.mutate().uri(eTx).build();
                        newRequest = new ServerHttpRequestDecorator(newRequest) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return bodyFlux;
                            }
                        };
                    }else{
                        newRequest = req;
                    }
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,newRequest.getURI());
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,route);
                    return chain.filter(exchange.mutate().request(newRequest).build());
                }));
    }

    /**
     * 获取路由
     * @param name
     * @return
     */
    public Route getRoute(String name){
        List<RouteDefinition> definitions = gatewayProperties.getRoutes();
        definitions = definitions.stream().filter(routeDefinition -> routeDefinition.getId().endsWith(name)).collect(Collectors.toList());
        Route route = null;
        if(!CollectionUtils.isEmpty(definitions)){
            List<String> patterns = new ArrayList<>();
            definitions.get(0).getPredicates().stream().forEach(a->{
                patterns.addAll(a.getArgs().values());
            });
            route = Route.async(definitions.get(0)).predicate(buildPredicate(patterns)).build();
        }
        return route;
    }

    public Predicate<ServerWebExchange> buildPredicate(List<String> patterns){
        PathRoutePredicateFactory factory = new PathRoutePredicateFactory();
        PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config();
        config.setPatterns(patterns);
        config.setMatchTrailingSlash(true);
        return factory.apply(config);
    }

}