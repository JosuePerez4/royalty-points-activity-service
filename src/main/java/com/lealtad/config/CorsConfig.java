package com.lealtad.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(
            @Value("${FRONTEND_CORS_PATTERNS:https://*.vercel.app,https://*.up.railway.app,http://localhost:*,http://127.0.0.1:*}") String patterns,
            @Value("${FRONTEND_URL:https://taller3-frontend-microservicios-production.up.railway.app}") String extraOrigins) {
        this.allowedOriginPatterns = mergeCsv(patterns, extraOrigins);
    }

    private static List<String> mergeCsv(String patterns, String extraOrigins) {
        List<String> out = new ArrayList<>();
        appendCsv(out, patterns);
        appendCsv(out, extraOrigins);
        return out;
    }

    private static void appendCsv(List<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(target::add);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
