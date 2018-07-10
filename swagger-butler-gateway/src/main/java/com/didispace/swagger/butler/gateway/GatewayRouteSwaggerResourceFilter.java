package com.didispace.swagger.butler.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;

/**
 * Created by xiaoyao9184 on 2018/7/9.
 */
@ConditionalOnProperty("swagger.butler.gateway.username")
public class GatewayRouteSwaggerResourceFilter implements GlobalFilter {

    private static Logger logger = LoggerFactory.getLogger(GatewayRouteSwaggerResourceFilter.class);

    @Autowired
    private GatewayRouteSwaggerResourceProperties gatewayRouteSwaggerResourceProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        if(req.getHeaders().containsKey("Authorization")){
            logger.debug("Already exists authorization header skip GatewayRouteSwaggerResourceFilter!");
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if(req.getURI().getRawPath().matches(".*api-docs.*")){
            logger.trace("GatewayRouteSwaggerResourceFilter start");

            GatewayRouteSwaggerResourceProperties.Route ra = gatewayRouteSwaggerResourceProperties.getRoutes().stream()
                    .filter(r -> route.getId().equals(r.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        if(gatewayRouteSwaggerResourceProperties.getPassword() != null &&
                                gatewayRouteSwaggerResourceProperties.getPassword() != null){
                            logger.debug("Use global authorization header(Http Basic) for route '{}' api-docs!",
                                    route.getId());
                            GatewayRouteSwaggerResourceProperties.Route global = new GatewayRouteSwaggerResourceProperties.Route();
                            global.setId("[global]");
                            global.setUsername(gatewayRouteSwaggerResourceProperties.getUsername());
                            global.setPassword(gatewayRouteSwaggerResourceProperties.getPassword());
                            return global;
                        }
                        return null;
                    });

            if(ra != null){
                @SuppressWarnings("StringBufferReplaceableByString")
                String basic = new StringBuffer()
                        .append(ra.getUsername())
                        .append(':')
                        .append(ra.getPassword())
                        .toString();
                basic = Base64.getEncoder().encodeToString(basic.getBytes());
                basic = "Basic " + basic;

                String authorization = basic;
                ServerHttpRequest request = exchange.getRequest().mutate()
                        .headers(httpHeaders -> httpHeaders.add("Authorization", authorization))
                        .build();
                return chain.filter(exchange.mutate().request(request).build());
            }
        }

        logger.debug("No authorization header(Http Basic) for route '{}' api-docs!",
                route.getId());
        return chain.filter(exchange);
    }
}
