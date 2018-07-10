package com.didispace.swagger.butler.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by xiaoyao9184 on 2018/7/5.
 */
@Configuration
@ConfigurationProperties(prefix = "swagger.butler.gateway", ignoreUnknownFields = true)
public class GatewayRouteSwaggerResourceProperties {

    /**
     * It will init and cache SwaggerResource when gateway server start up.
     */
    private boolean autoStart = true;

    /**
     * Request for gateway itself to get remote service's SwaggerResource
     */
    private boolean requestGatewayRoute = true;

    /**
     * Use route id to group remote service's SwaggerResource
     */
    private boolean mapRouteIdPrefixName = true;

    /**
     * Map direct SwaggerResource url for remote or gateway
     */
    private boolean mapDirectUrl = false;

    /**
     * Username for api document viewing permission
     */
    private String username;

    /**
     * Password for api document viewing permission
     */
    private String password;

    /**
     * Settings for a single route
     */
    private Set<Route> routes = new HashSet<>();

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isRequestGatewayRoute() {
        return requestGatewayRoute;
    }

    public void setRequestGatewayRoute(boolean requestGatewayRoute) {
        this.requestGatewayRoute = requestGatewayRoute;
    }

    public boolean isMapRouteIdPrefixName() {
        return mapRouteIdPrefixName;
    }

    public void setMapRouteIdPrefixName(boolean mapRouteIdPrefixName) {
        this.mapRouteIdPrefixName = mapRouteIdPrefixName;
    }

    public boolean isMapDirectUrl() {
        return mapDirectUrl;
    }

    public void setMapDirectUrl(boolean mapDirectUrl) {
        this.mapDirectUrl = mapDirectUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(Set<Route> routes) {
        this.routes = routes;
    }

    /**
     * authorization for GatewayRouteSwaggerResource
     */
    public static class Route {

        /**
         * Route ID
         */
        private String id;

        /**
         * Username for api document viewing permission
         */
        private String username;

        /**
         * Password for api document viewing permission
         */
        private String password;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
