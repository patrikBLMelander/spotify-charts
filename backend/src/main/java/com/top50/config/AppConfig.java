package com.top50.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private String corsAllowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Parse allowed origins from environment variable
        // If "*" or empty, allow all origins
        if (corsAllowedOrigins == null || corsAllowedOrigins.trim().isEmpty() || corsAllowedOrigins.equals("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            // Split by comma and trim each origin, remove trailing slashes
            List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                    .filter(s -> !s.isEmpty())
                    .toList();
            config.setAllowedOrigins(origins);
        }
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
