package com.addf.backend.ngxdd.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ngxddOpenApi(@Value("${info.app.version}") String appVersion) {
        return new OpenAPI().info(new Info()
                .title("NGX Dynamic Dashboard Framework — Microservice API")
                .description("""
                        Backend service for the Angular NGX Dynamic Dashboard Framework. \
                        Provides the REST/A2UI chat assistant API used by the dashboard's \
                        assistant panel, with MCP and A2A tool-server capabilities on the roadmap.""")
                .version(appVersion)
                .contact(new Contact().name("ngx-dynamic-dashboard-framework")));
    }
}
