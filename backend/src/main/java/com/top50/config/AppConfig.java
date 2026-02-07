package com.top50.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}")
    private String corsAllowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Parse allowed origins from environment variable
        // Default includes common localhost ports for development
        List<String> allowedOrigins;
        
        if (corsAllowedOrigins == null || corsAllowedOrigins.trim().isEmpty()) {
            // Default to localhost ports for development
            allowedOrigins = List.of("http://localhost:3000", "http://localhost:3001");
        } else if (corsAllowedOrigins.equals("*")) {
            // If "*" is specified, we can't use it with allowCredentials=true
            // So we'll use origin patterns instead, but this won't work with credentials
            // Better to use specific origins
            log.warn("CORS_ALLOWED_ORIGINS is set to '*'. This won't work with allowCredentials=true. Using default localhost origins.");
            allowedOrigins = List.of("http://localhost:3000", "http://localhost:3001");
        } else {
            // Split by comma and trim each origin, remove trailing slashes
            allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        log.info("CORS configured with allowed origins: {}", allowedOrigins);
        
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
