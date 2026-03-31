package org.example.aipassagecreator.AIAgent.Tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class WebCrawlerToolTest {
    @Resource
    private WebCrawlerTool webCrawlerTool;

    @Test
    void crawlWebPage() {
        String result = webCrawlerTool.crawlWebPage("诗歌剧");
        System.out.println(result);
    }
}