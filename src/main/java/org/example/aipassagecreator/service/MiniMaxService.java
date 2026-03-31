package org.example.aipassagecreator.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.aipassagecreator.config.MiniMaxConfig;
import org.example.aipassagecreator.domain.constant.ImageMethodEnum;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.aipassagecreator.domain.constant.ArticleConstant.PICSUM_URL_TEMPLATE;
/*
* MiniMax AI 图片生成服务
 */
@Service
@Slf4j
public class MiniMaxService implements ImageSearchService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Resource
    private MiniMaxConfig miniMaxConfig;

    private final OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String searchImage(String keywords) {
        try {
            // 调用 MiniMax API 生成单张图片
            List<String> base64Images = generateImages(keywords);
            if (base64Images == null || base64Images.isEmpty()) {
                log.error("MiniMax API 未返回图片, keywords={}", keywords);
                return null;
            }
            return base64Images.get(0);
        } catch (IOException e) {
            log.error("MiniMax API 调用异常, keywords={}", keywords, e);
            return null;
        }
    }

    /**
     * 批量生成图片
     *
     * @param prompt  图片描述
     * @return Base64 编码的图片列表
     */
    public List<String> generateImages(String prompt) throws IOException {
        String url = miniMaxConfig.getBaseUrl();

        // 构建请求体
        JsonObject payload = new JsonObject();
        payload.addProperty("model", miniMaxConfig.getModel());
        payload.addProperty("prompt", prompt);
        payload.addProperty("aspect_ratio", miniMaxConfig.getAspectRatio());
        payload.addProperty("response_format", miniMaxConfig.getResponseFormat());

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + miniMaxConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON, payload.toString()))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("MiniMax API 调用失败: {}, code={}", response.code(), response.message());
                return null;
            }

            String responseBody = response.body().string();
            return extractBase64Images(responseBody);
        }
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.MINIMAX;
    }

    @Override
    public String getFallbackImage(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

    /**
     * 从响应中提取 Base64 编码的图片
     *
     * @param responseBody 响应体
     * @return Base64 图片列表
     */
    private List<String> extractBase64Images(String responseBody) {
        List<String> images = new ArrayList<>();

        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            var dataElement = jsonObject.get("data");
            if (dataElement == null || dataElement.isJsonNull()) {
                log.warn("MiniMax API 响应中 data 字段为空, responseBody={}", responseBody);
                return images;
            }

            // base64 模式下 data 是对象，不是数组
            JsonObject dataObject = dataElement.getAsJsonObject();
            if (dataObject.has("image_base64")) {
                JsonArray imageBase64Array = dataObject.getAsJsonArray("image_base64");
                for (int i = 0; i < imageBase64Array.size(); i++) {
                    // 包装为标准 data URL 格式，供 ImageData.fromUrl() 正确识别
                    String base64 = imageBase64Array.get(i).getAsString();
                    images.add("data:image/jpeg;base64," + base64);
                }
            }
        } catch (Exception e) {
            log.error("解析 MiniMax API 响应异常, responseBody={}", responseBody, e);
        }

        return images;
    }
}
