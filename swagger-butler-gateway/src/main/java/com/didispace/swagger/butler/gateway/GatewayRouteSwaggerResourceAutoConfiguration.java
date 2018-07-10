package com.didispace.swagger.butler.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebFlux;

import javax.annotation.Resource;
import java.util.Base64;

/**
 * Created by xiaoyao9184 on 2018/7/9.
 */
@Configuration
@ConditionalOnClass({EnableSwagger2WebFlux.class,GatewayAutoConfiguration.class})
@Import({GatewayRouteSwaggerResourceProcessor.class,
        GatewayRouteSwaggerResourceFilter.class})
public class GatewayRouteSwaggerResourceAutoConfiguration {

    private static Logger logger = LoggerFactory.getLogger(GatewayRouteSwaggerResourceAutoConfiguration.class);

    @Autowired
    private GatewayRouteSwaggerResourceProperties gatewayRouteSwaggerResourceProperties;

    @Autowired
    private GatewayRouteSwaggerResourceProcessor gatewayRouteSwaggerResourceProcessor;

    @Resource
    private Environment environment;

    @Bean
    @ConditionalOnMissingBean
    public WebClient gatewayRouteWebClient(){
        WebClient.Builder builder = WebClient.builder();

        //Proxy for system
        Boolean proxySet = environment.getProperty("proxySet", Boolean.class, false);
        String proxyHost = environment.getProperty("http.proxyHost");
        Integer proxyPort = environment.getProperty("http.proxyPort",Integer.class);
        if(proxySet != null && proxySet){
            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(options -> options
                    .httpProxy(addressSpec -> {
                        return addressSpec.host(proxyHost).port(proxyPort); // or any other method on addressSpec
                    }));
            builder.clientConnector(connector);
        }

        //Authorization for SwaggerResource
        //Only Http Basic
        if(gatewayRouteSwaggerResourceProperties.getUsername() != null &&
                gatewayRouteSwaggerResourceProperties.getPassword() != null){
            logger.debug("WebClient use Http Basic Authorization header!");
            @SuppressWarnings("StringBufferReplaceableByString")
            String basic = new StringBuffer()
                    .append(gatewayRouteSwaggerResourceProperties.getUsername())
                    .append(':')
                    .append(gatewayRouteSwaggerResourceProperties.getPassword())
                    .toString();
            basic = Base64.getEncoder().encodeToString(basic.getBytes());
            basic = "Basic " + basic;
            //Http Basic
            builder.defaultHeader("Authorization",basic);
        }

        return builder.build();
    }

    @Bean
    @Primary
    public GatewayRouteSwaggerResourcesProvider swaggerResourcesProvider(){
        return new GatewayRouteSwaggerResourcesProvider(
                gatewayRouteSwaggerResourceProperties,
                gatewayRouteSwaggerResourceProcessor);
    }
}
