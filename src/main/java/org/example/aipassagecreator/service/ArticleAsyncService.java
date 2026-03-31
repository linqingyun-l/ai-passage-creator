package org.example.aipassagecreator.service;

import com.google.common.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aipassagecreator.AIAgent.AIService.ArticleAgentService;
import org.example.aipassagecreator.AIAgent.Manager.SseEmitterManager;
import org.example.aipassagecreator.domain.DTO.Article.ArticleState;
import org.example.aipassagecreator.domain.PO.Article;
import org.example.aipassagecreator.domain.PO.User;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.domain.constant.ArticlePhaseEnum;
import org.example.aipassagecreator.domain.constant.ArticleStatusEnum;
import org.example.aipassagecreator.domain.constant.SseMessageTypeEnum;
import org.example.aipassagecreator.utils.GsonUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ArticleAsyncService {

    @Resource
    private ArticleAgentService articleAgentService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private ArticleService articleService;

    /**
     * 异步执行文章生成
     *
     * @param taskId 任务ID
     * @param topic  选题
     */
    @Async("articleExecutor")
    public void executeArticleGeneration(String taskId, String topic,String style) {
        try {
            // 更新状态为处理中
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING, null);
            
            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);
            
            // 执行智能体编排,并通过 SSE 推送进度
            articleAgentService.executeArticleGeneration(state, message -> {
                handleAgentMessage(taskId, message, state);
            });
            
            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);
            
            // 更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);
            
            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));
            
            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);
            
            log.info("异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("异步任务失败, taskId={}", taskId, e);
            
            // 更新状态为失败
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            
            // 推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            
            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 延迟等待SSE连接创建
     */
    private void waitForSseConnection(String taskId, String topic, String style) {
        log.info("阶段1异步任务开始, taskId={}, topic={}, style={}", taskId, topic, style);

        // 等待一段时间，确保前端有足够时间建立 SSE 连接
        // 正常情况下，emitter 在 createArticle 中已经创建，所以 exists 应该立即返回 true
        // 如果返回 false，说明时序有问题，需要等待
        int waitCount = 0;
        final int maxWaitCount = 10; // 最多等待 3 秒 (10 * 300ms)
        final int waitInterval = 300; // 每次等待 300ms

        boolean emitterExists = sseEmitterManager.exists(taskId);
        log.info("初始检查: emitter 是否存在={}, taskId={}", emitterExists, taskId);

        while (!emitterExists && waitCount < maxWaitCount) {
            log.info("SSE 连接尚未建立，等待中... taskId={}, waitCount={}", taskId, waitCount);
            try {
                Thread.sleep(waitInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("异步任务等待被中断, taskId={}", taskId);
                throw new RuntimeException(e);
            }
            waitCount++;
            emitterExists = sseEmitterManager.exists(taskId);
        }

        if (!emitterExists) {
            log.error("SSE 连接在 {}ms 内未建立，放弃任务, taskId={}", maxWaitCount * waitInterval, taskId);
            throw new RuntimeException();
        }

        log.info("SSE 连接已确认存在, taskId={}", taskId);
    }

    /**
     * 阶段1：异步生成标题方案
     *
     * @param taskId 任务ID
     * @param topic  选题
     * @param style  文章风格（可为空）
     */
    @Async("articleExecutor")
    public void executePhase1(String taskId, String topic, String style) {

        waitForSseConnection(taskId, topic, style);
        try {
            // 更新状态和阶段
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING, null);
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);

            // 执行阶段1：生成标题方案
            articleAgentService.executePhase1_GenerateTitles(state, message -> {
                handleAgentMessage(taskId, message, state);
            });

            // 保存标题方案到数据库
            articleService.saveTitleOptions(taskId, state.getTitleOptions());

            // 更新阶段为等待选择标题
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);

            // 推送标题方案生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("titleOptions", state.getTitleOptions());
            sendSseMessage(taskId, SseMessageTypeEnum.TITLES_GENERATED, data);

            log.info("阶段1异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段1异步任务失败, taskId={}", taskId, e);

            // 更新状态为失败
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());

            // 推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));

            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段2：异步生成大纲（用户确认标题后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase2(String taskId) {
        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setUserDescription(article.getUserDescription());

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 执行阶段2：生成大纲
            articleAgentService.executePhase2_GenerateOutline(state, message -> {
                handleAgentMessage(taskId, message, state);
            });

            // 保存大纲到数据库
            Article articleToUpdate = articleService.getByTaskId(taskId);
            articleToUpdate.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
            articleService.updateById(articleToUpdate);

            // 更新阶段为等待编辑大纲
            articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);

            // 推送大纲生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("outline", state.getOutline().getSections());
            sendSseMessage(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, data);

            log.info("阶段2异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段2异步任务失败, taskId={}", taskId, e);

            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段3：异步生成正文+配图（用户确认大纲后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase3(String taskId) {
        log.info("阶段3异步任务开始, taskId={}", taskId);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());

            // 从数据库获取允许的配图方式
            List<String> enabledMethods = null;
            if (article.getEnabledImageMethods() != null) {
                enabledMethods = GsonUtils.fromJson(
                        article.getEnabledImageMethods(),
                        new TypeToken<List<String>>(){}
                );
            }
            state.setEnabledImageMethods(enabledMethods);

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 设置大纲
            List<ArticleState.OutlineSection> outlineSections = GsonUtils.fromJson(
                    article.getOutline(),
                    new TypeToken<List<ArticleState.OutlineSection>>(){}
            );
            ArticleState.OutlineResult outlineResult = new ArticleState.OutlineResult();
            outlineResult.setSections(outlineSections);
            state.setOutline(outlineResult);

            // 执行阶段3：生成正文+配图
            articleAgentService.executePhase3_GenerateContent(state, message -> {
                handleAgentMessage(taskId, message, state);
            });

            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);

            // 更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);

            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));

            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);

            log.info("阶段3异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段3异步任务失败, taskId={}", taskId, e);

            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 处理智能体消息并推送
     */
    private void handleAgentMessage(String taskId, String message, ArticleState state) {
        Map<String, Object> data = buildMessageData(message, state);
        if (data != null) {
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
        }
    }

    /**
     * 构建消息数据
     */
    private Map<String, Object> buildMessageData(String message, ArticleState state) {
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();

        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING,
                    message.substring(streamingPrefix2.length()));
        }

        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING,
                    message.substring(streamingPrefix3.length()));
        }

        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }

        // 处理完成消息（枚举值）
        return buildCompleteMessageData(message, state);
    }

    /**
     * 构建流式输出数据
     */
    private Map<String, Object> buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return data;
    }

    /**
     * 构建图片完成数据
     */
    private Map<String, Object> buildImageCompleteData(String imageJson) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
        return data;
    }

    /**
     * 构建完成消息数据
     */
    private Map<String, Object> buildCompleteMessageData(String message, ArticleState state) {
        Map<String, Object> data = new HashMap<>();

        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        } else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        } else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        } else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        } else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        } else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        } else {
            return null;
        }

        return data;
    }

    /**
     * 发送 SSE 消息
     */
    private void sendSseMessage(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.putAll(additionalData);
        sseEmitterManager.send(taskId, GsonUtils.toJson(data));
    }

}