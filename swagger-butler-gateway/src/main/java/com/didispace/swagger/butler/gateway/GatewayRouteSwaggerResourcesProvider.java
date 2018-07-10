package com.didispace.swagger.butler.gateway;

import org.springframework.boot.web.reactive.context.ReactiveWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import java.util.List;

/**
 * Created by xiaoyao9184 on 2018/7/4.
 */
public class GatewayRouteSwaggerResourcesProvider implements SwaggerResourcesProvider {

    private final GatewayRouteSwaggerResourceProperties gatewayRouteSwaggerResourceProperties;
    private final GatewayRouteSwaggerResourceProcessor gatewayRouteSwaggerResourceProcessor;

    private List<SwaggerResource> serviceSwaggerResources;


    public GatewayRouteSwaggerResourcesProvider(
            GatewayRouteSwaggerResourceProperties gatewayRouteSwaggerResourceProperties,
            GatewayRouteSwaggerResourceProcessor gatewayRouteSwaggerResourceProcessor) {
        this.gatewayRouteSwaggerResourceProperties = gatewayRouteSwaggerResourceProperties;
        this.gatewayRouteSwaggerResourceProcessor = gatewayRouteSwaggerResourceProcessor;
    }

    @EventListener(ReactiveWebServerInitializedEvent.class)
    public void autoInit(){
        if(gatewayRouteSwaggerResourceProperties.isAutoStart()){
            this.init();
        }
    }

    public void init() {
        this.serviceSwaggerResources = gatewayRouteSwaggerResourceProcessor.init();
    }

    @Override
    public List<SwaggerResource> get() {
        return this.serviceSwaggerResources;
    }

}
