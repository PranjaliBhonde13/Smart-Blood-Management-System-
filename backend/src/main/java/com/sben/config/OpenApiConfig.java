package com.sben.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated Swagger UI (/swagger-ui.html).
 * The JWT security scheme is registered here in Module 1.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sbenOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Smart Blood Emergency Network API")
                .description("REST API connecting patients, donors, hospitals and blood banks.")
                .version("v1")
                .contact(new Contact().name("SBEN").email("Contact@kinetikalabs.com")));
    }
}
