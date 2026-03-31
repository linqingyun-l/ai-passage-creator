package org.example.aipassagecreator.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class EmojiPackServiceTest {
    @Resource
    private EmojiPackService emojiPackService;
    @Test
    void searchImage() {
        String image = emojiPackService.searchImage("奶龙");
        System.out.println(image);
    }
}