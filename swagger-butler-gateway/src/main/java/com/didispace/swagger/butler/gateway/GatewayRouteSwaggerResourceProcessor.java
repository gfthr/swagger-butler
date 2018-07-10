package com.didispace.swagger.butler.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import springfox.documentation.swagger.web.SwaggerResource;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Created by xiaoyao9184 on 2018/7/5.
 */
public class GatewayRouteSwaggerResourceProcessor {

    private static final String SWAGGER_RESOURCES = "swagger-resources";
    private static final String FILTER_REWRITE_PATH = "RewritePath";
    private static final String PREDICATE_PATH = "Path";
    
    private static Logger logger = LoggerFactory.getLogger(GatewayRouteSwaggerResourceProcessor.class);

    @Autowired
    private ServerProperties serverProperties;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private RouteLocator routeLocator;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private GatewayRouteSwaggerResourceProperties gatewayRouteSwaggerResourceProperties;

    @Autowired
    @Qualifier("gatewayRouteWebClient")
    private WebClient client;

    public List<SwaggerResource> init(){
        logger.debug("Init gateway SwaggerResource");

        //Route
        Flux<Route> routes = routeLocator.getRoutes();

        //SwaggerResource
        List<SwaggerResource> resources = routes.flatMap(r -> gatewayRouteSwaggerResource(r))
                .collectList()
                .onErrorResume(t -> {
                    logger.error("Init error!",t);
                    return Mono.empty();
                })
                .block();

        return resources;
    }
    
    /**
     * get SwaggerResource from route
     * @param route Route
     * @return
     */
    public Flux<SwaggerResource> gatewayRouteSwaggerResource(Route route) {
        //Only support Path Predicate
        //Usually only one Path Predicate will be set
        logger.debug("Get route path from Route '{}' PathPredicate!", route.getId());

        //path is route path on this server
        List<String> routePaths = this.routeDefinitionLocator.getRouteDefinitions()
                .filter(rd -> rd.getId().equals(route.getId()))
                .flatMap(rd -> Flux.fromStream(rd.getPredicates().stream()))
                .filter(pd -> PREDICATE_PATH.equals(pd.getName()))
                .filter(pd -> pd.getArgs().size() == 1)
                .map(pd -> pd.getArgs().values().toArray()[0].toString())
                .filter(p -> p.matches(".*\\*\\*$"))
                .filter(Objects::nonNull)
                .collectList()
                .block();
        if(routePaths.isEmpty()){
            logger.debug("Cant handle no Path route '{}'!",
                    route.getId());
        }

        //send it self or send to target
        if(gatewayRouteSwaggerResourceProperties.isRequestGatewayRoute()){
            return Flux.fromStream(routePaths.stream())
                    .doOnNext(p -> logger.debug("Handle route '{}' path '{}' by remote direct",
                            route.getId(), p))
                    .flatMap(p -> gatewayRouteSwaggerResource(route,routeBuilder(p)));
        }else{
            //Only support RewritePath Filter
            //Usually only one RewritePath Filter will be set
            logger.debug("Get rewrite path from Route '{}' RewritePathFilter!", route.getId());

            //path is route path on this server
            List<Tuple2> rewritePaths = this.routeDefinitionLocator.getRouteDefinitions()
                    .filter(rd -> rd.getId().equals(route.getId()))
                    .map(rd -> rd.getFilters().stream()
                            .filter(f -> FILTER_REWRITE_PATH.equals(f.getName()))
                            .findAny()
                            .orElse(new FilterDefinition()))
                    .filter(fd -> fd.getName() != null)
                    .map(fd -> Tuples.fromArray(fd.getArgs().values().toArray(new String[]{})))
                    .collectList()
                    .block();
            if(rewritePaths.isEmpty()){
                logger.debug("Cant handle no Rewrite Path route '{}'!",
                        route.getId());
                return Flux.empty();
            }

            return Flux.fromStream(routePaths.stream())
                    .doOnNext(p -> logger.debug("Handle route '{}' path '{}' by remote direct",
                            route.getId(), p))
                    .flatMap(p -> remoteDirectSwaggerResource(route, routeBuilder(p), p, rewritePaths));
        }
    }

    /**
     * get route path builder
     * @param routePath route path
     * @return
     */
    public UriComponentsBuilder routeBuilder(String routePath){
        return UriComponentsBuilder.newInstance()
                .path(routePath.replaceAll("\\*\\*$","{sr}"))
                .host("localhost")
                .port(serverProperties.getPort())
                .scheme("http");
    }

    /**
     * get SwaggerResource by route's path from gateway
     * request StringFox 2.7.0
     * @link https://github.com/springfox/springfox/issues/1706
     * @param route Route
     * @param builder route path builder
     * @return
     */
    public Flux<SwaggerResource> gatewayRouteSwaggerResource(Route route, UriComponentsBuilder builder){
        URI requestUrl = builder
                .buildAndExpand(SWAGGER_RESOURCES)
                .toUri();

        return uriSwaggerResource(route,requestUrl)
                .map(sr -> map2RouteSwaggerResource(sr,route,builder,builder));
    }

    /**
     * get SwaggerResource by rewrite paths from remote
     * @param route Route
     * @param builder route path builder
     * @param routePath route path
     * @param rewritePaths rewrite paths
     * @return
     */
    public Flux<SwaggerResource> remoteDirectSwaggerResource(Route route, UriComponentsBuilder builder, String routePath, List<Tuple2> rewritePaths){
        //use rewritePaths to replace

        final String[] targetPath = {routePath.replaceAll("\\*\\*$","{st}")};
        rewritePaths.forEach(rp -> {
            String replacement = rp.getT2().toString().replace("$\\", "$");
            targetPath[0] = targetPath[0].replaceAll(
                    rp.getT1().toString(),
                    replacement);
        });

        UriComponentsBuilder requestBuilder = UriComponentsBuilder.fromUri(route.getUri())
                .path(targetPath[0]);
        URI requestUrl = requestBuilder
                .buildAndExpand(SWAGGER_RESOURCES)
                .toUri();

        return uriSwaggerResource(route,requestUrl)
                .map(sr -> map2RouteSwaggerResource(sr,route,builder,requestBuilder));
    }

    /**
     * get SwaggerResource from URI
     * @param requestUrl
     * @return
     */
    public Flux<SwaggerResource> uriSwaggerResource(Route route, URI requestUrl){
        logger.debug("Route {} Request URL {} for SwaggerResource",route.getId(), requestUrl);

        WebClient.RequestHeadersSpec rhs = client
                .get()
                .uri(requestUrl)
                .accept(MediaType.APPLICATION_JSON_UTF8);

        gatewayRouteSwaggerResourceProperties.getRoutes().stream()
                .filter(r -> route.getId().equals(r.getId()))
                .findFirst()
                .ifPresent(r -> {
                    logger.debug("Route {} Request URL {} use Http Basic Authorization header!",
                            route.getId(),requestUrl);
                    @SuppressWarnings("StringBufferReplaceableByString")
                    String basic = new StringBuffer()
                            .append(r.getUsername())
                            .append(':')
                            .append(r.getPassword())
                            .toString();
                    basic = Base64.getEncoder().encodeToString(basic.getBytes());
                    basic = "Basic " + basic;
                    //Http Basic
                    rhs.header("Authorization",basic);
                });

        Flux<SwaggerResource> swaggerResource = rhs
                .retrieve()
                .bodyToFlux(SwaggerResource.class);

        return swaggerResource
                .onErrorResume(t -> t.getMessage().contains("404"),
                        e -> {
                            logger.warn("Request URL {} is not a swagger endpoint!",requestUrl);
                            return Mono.empty();
                        })
                .onErrorResume(WebClientResponseException.class, 
                        e -> {
                            logger.warn("Request URL {} error, may be security authentication issue!",requestUrl);
                            SwaggerResource sr = new SwaggerResource();
                            sr.setName(e.getStatusText());
                            sr.setUrl(requestUrl.toString());
                            return Mono.just(sr);
                        })
                .onErrorResume(RuntimeException.class,
                        e -> {
                            logger.warn("Request URL {} error!",requestUrl);
                            return Mono.empty();
                        })
                .onErrorResume(IOException.class,
                        e -> {
                            logger.warn("Request URL {} error!",requestUrl);
                            return Mono.empty();
                        });
    }

    /**
     * map target SwaggerResource to route SwaggerResource
     * @param requestBuilder
     * @param resource
     * @return
     */
    public SwaggerResource map2RouteSwaggerResource(
            SwaggerResource resource,
            Route route,
            UriComponentsBuilder routeBuilder,
            UriComponentsBuilder requestBuilder){

        String path = resource.getUrl();
        String docUrl;
        if(path.startsWith("http")){
            //when error the SwaggerResource url will be set Request URL
            docUrl = path;
            logger.error("Cant point to the correct Api document, " +
                    "because this absolute path is point to Request URL SwaggerResource: {}",
                    docUrl);
        }else{
            UriComponentsBuilder resourceUrlBuilder = UriComponentsBuilder.newInstance();

            if(routeBuilder.equals(requestBuilder)){
                //relative path for gateway
                logger.debug("SwaggerResource '{}' comes from '{}' by gateway route.",
                        resource.getName(), route.getId());

                resourceUrlBuilder.path(routeBuilder.build().getPath());
                logger.debug("Api document path will point to gateway route.");
            }else if(gatewayRouteSwaggerResourceProperties.isMapDirectUrl()){
                //absolute path for remote
                //request SwaggerUI 2.9
                logger.debug("SwaggerResource '{}' comes from '{}' by remote direct.",
                        resource.getName(), route.getId());

                resourceUrlBuilder = requestBuilder;
                logger.debug("Api document path configured point to remote direct" +
                        "(make sure Swagger UI supported).");
            }else{
                //relative path for remote cant use
                //change to relative path for gateway
                logger.debug("SwaggerResource '{}' comes from '{}' by remote direct.",
                        resource.getName(), route.getId());

                resourceUrlBuilder.path(routeBuilder.build().getPath());
                logger.debug("Api document path configured point to gateway route.");
            }

            if(path.startsWith("/")){
                path = path.substring(1);
            }
            UriComponents pathUriComponents = UriComponentsBuilder.fromUriString(path).build();
            docUrl = resourceUrlBuilder
                    .queryParams(pathUriComponents.getQueryParams())
                    .buildAndExpand(pathUriComponents.getPath())
                    .toString();
            logger.debug("Api document path point {}.", docUrl);

            resource.setUrl(docUrl);
        }


        if(gatewayRouteSwaggerResourceProperties.isMapRouteIdPrefixName()){
            resource.setName("[" + route.getId() + "]" + resource.getName());
        }
        return resource;
    }
}
