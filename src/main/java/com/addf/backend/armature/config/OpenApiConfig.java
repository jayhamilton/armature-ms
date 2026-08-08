package com.addf.backend.armature.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI armatureOpenApi(@Value("${info.app.version}") String appVersion) {
        return new OpenAPI().info(new Info()
                .title("Armature Microservice API")
                .description("""
                        Backend service for Armature. \
                        Provides the REST/A2UI chat assistant API used by the dashboard's \
                        assistant panel, with MCP and A2A tool-server capabilities on the roadmap.""")
                .version(appVersion)
                .contact(new Contact().name("armature-ms")));
    }
}
