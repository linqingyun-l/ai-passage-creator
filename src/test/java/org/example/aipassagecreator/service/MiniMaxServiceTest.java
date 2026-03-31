package org.example.aipassagecreator.service;

import jakarta.annotation.Resource;
import org.example.aipassagecreator.domain.DTO.image.ImageData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@SpringBootTest
class MiniMaxServiceTest {
    @Resource
    private MiniMaxService miniMaxService;
    @Resource
    private CosService cosService;


    @Test
    public void generateImages() throws IOException {
        List<String> list = miniMaxService.generateImages("碧蓝航线信农");
        System.out.println(list);
        for (String base64Image : list) {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            ImageData imageData = ImageData.fromBytes(imageBytes, "image/jpeg");
            String s1 = cosService.uploadFromDataUrl(imageData, "test");
            System.out.println(s1);
        }
    }
}