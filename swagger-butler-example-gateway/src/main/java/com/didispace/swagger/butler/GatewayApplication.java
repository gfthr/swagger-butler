package com.didispace.swagger.butler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebFlux;

/**
 * Created by xiaoyao9184 on 2018/7/9.
 */
@SpringBootApplication
@EnableSwagger2WebFlux
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class);
    }

}
