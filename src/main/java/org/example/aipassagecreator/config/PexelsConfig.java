package org.example.aipassagecreator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pexels")
@Data
public class PexelsConfig {

    /**
     * API Key
     */
    private String apiKey;
}