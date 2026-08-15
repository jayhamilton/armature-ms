package com.addf.backend.armature.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

// Every hand-written @RestController here (AgentController, EndpointController,
// DataSourceController) already opts into cross-origin requests via @CrossOrigin,
// same permissive-for-dev posture as this. The MCP server's /sse and
// /mcp/message endpoints aren't a controller of ours to annotate - they're
// registered directly by spring-ai-starter-mcp-server-webmvc's autoconfiguration -
// so a Servlet Filter-level CorsFilter is what reaches them too, applied here to
// every path rather than duplicated per-controller.
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
