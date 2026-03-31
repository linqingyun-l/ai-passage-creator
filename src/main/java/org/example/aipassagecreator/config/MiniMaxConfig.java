package org.example.aipassagecreator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MiniMax API 配置
 */
@Configuration
@ConfigurationProperties(prefix = "minimax")
@Data
public class MiniMaxConfig {

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API 基础地址
     */
    private String baseUrl = "https://api.minimaxi.com/v1/image_generation";

    /**
     * 使用的模型
     */
    private String model = "image-01";

    /**
     * 图片宽高比
     */
    private String aspectRatio = "16:9";

    /**
     * 响应格式
     */
    private String responseFormat = "base64";
}