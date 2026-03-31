package org.example.aipassagecreator.AIAgent.AIService;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aipassagecreator.AIAgent.ImageSearchService;
import org.example.aipassagecreator.AIAgent.Tools.WebCrawlerTool;
import org.example.aipassagecreator.domain.DTO.Article.ArticleState;
import org.example.aipassagecreator.domain.DTO.image.ImageRequest;
import org.example.aipassagecreator.domain.constant.ArticleStyleEnum;
import org.example.aipassagecreator.domain.constant.ImageMethodEnum;
import org.example.aipassagecreator.domain.constant.PromptConstant;
import org.example.aipassagecreator.domain.constant.SseMessageTypeEnum;
import org.example.aipassagecreator.exception.ErrorCode;
import org.example.aipassagecreator.exception.ThrowUtils;
import org.example.aipassagecreator.service.CosService;
import org.example.aipassagecreator.service.ImageServiceStrategy;
import org.example.aipassagecreator.utils.GsonUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleAgentService {

    @Resource
    private DashScopeChatModel chatModel;

    @Resource
    private ImageServiceStrategy imageServiceStrategy;


    /**
     * 获取所有配图方式的完整描述
     */
    private String getAllMethodsDescription() {
        return """
               - PEXELS: 适合真实场景、产品照片、人物照片、自然风景等写实图片
               - MINIMAX: 适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片
               - MERMAID: 适合流程图、架构图、时序图、关系图、甘特图等结构化图表
               - ICONIFY: 适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）
               - EMOJI_PACK: 适合表情包、搞笑图片、轻松幽默的配图
               - SVG_DIAGRAM: 适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）
               """;
    }

    /**
     * 获取配图方式的使用说明
     */
    private String getMethodUsageDescription(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "适合真实场景、产品照片、人物照片、自然风景等写实图片";
            case MINIMAX -> "适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片";
            case MERMAID -> "适合流程图、架构图、时序图、关系图、甘特图等结构化图表";
            case ICONIFY -> "适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）";
            case EMOJI_PACK -> "适合表情包、搞笑图片、轻松幽默的配图";
            case SVG_DIAGRAM -> "适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）";
            default -> method.getDescription();
        };
    }

    /**
     * 执行完整的文章生成流程
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题
            log.info("智能体1：开始生成标题, taskId={}", state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());

            // 智能体2：生成大纲（流式输出）
            log.info("智能体2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("文章生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("文章生成失败: " + e.getMessage(), e);
        }
    }
    /**
     * 智能体1：生成标题
     */
    private void agent1GenerateTitle(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", state.getTopic())+ getStylePrompt(state.getStyle());//文章书写风格

        String content = callLlm(prompt);
        ArticleState.TitleResult titleResult = parseJsonResponse(content, ArticleState.TitleResult.class, "标题");
        state.setTitle(titleResult);
        log.info("智能体1：标题生成成功, mainTitle={}", titleResult.getMainTitle());
    }
    /**
     * 智能体2：生成大纲（流式输出）
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 构建 prompt，根据是否有用户补充描述插入对应部分
        String descriptionSection = "";
        if (state.getUserDescription() != null && !state.getUserDescription().trim().isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}", state.getUserDescription());
        }

        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{descriptionSection}", descriptionSection)
                + getStylePrompt(state.getStyle());

        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成成功, sections={}", outlineResult.getSections().size());
    }
    /**
     * 智能体3：生成正文（流式输出）
     */
    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText)+getStylePrompt(state.getStyle());//文章书写风格

        // 流式调用，内容会批量发送（每 100ms）
        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);

        state.setContent(content);
        log.info("智能体3：正文生成成功, length={}", content.length());
    }
    /**
     * 智能体4：分析配图需求
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        // 构建可用配图方式说明
        String availableMethods = buildAvailableMethodsDescription(state.getEnabledImageMethods());

        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent())
                .replace("{availableMethods}", availableMethods);

        String content = callLlm(prompt);
        ArticleState.Agent4Result agent4Result = parseJsonResponse(
                content,
                ArticleState.Agent4Result.class,
                "配图需求"
        );

        // 更新正文为包含占位符的版本
        state.setContent(agent4Result.getContentWithPlaceholders());
        state.setImageRequirements(agent4Result.getImageRequirements());
        log.info("智能体4：配图需求分析成功, count={}, 已在正文中插入占位符",
                agent4Result.getImageRequirements().size());
        log.info("智能体4：配图需求分析成功,result={}", agent4Result);
    }
    /**
     * 智能体5：生成配图（串行执行，支持混用多种配图方式，统一上传到 COS）
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始获取配图, position={}, imageSource={}, keywords={}",
                    requirement.getPosition(), imageSource, requirement.getKeywords());

            // 构建图片请求对象
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    .build();

            // 使用策略模式获取图片并统一上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);

            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 创建配图结果（URL 已经是 COS 地址）
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：配图获取并上传成功, position={}, method={}, cosUrl={}",
                    requirement.getPosition(), method.getValue(), cosUrl);
        }

        state.setImages(imageResults);
        log.info("智能体5：所有配图生成并上传完成, count={}", imageResults.size());
    }

    /**
     * 图文合成：将配图插入正文对应位置
     */
    private void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();

        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }

        // 第一步：替换占位符 {{IMAGE_PLACEHOLDER_N}} 和 {{ICON_PLACEHOLDER_N}}
        String fullContent = replacePlaceholders(content, images);
        log.info("图文合成：占位符替换完成");

        state.setFullContent(fullContent);
        log.info("图文合成完成, fullContentLength={}", fullContent.length());
    }

    /**
     * 替换正文中的占位符为实际图片
     */
    private String replacePlaceholders(String content, List<ArticleState.ImageResult> images) {
        for (ArticleState.ImageResult image : images) {
            String placeholderId = image.getPlaceholderId();
            if (placeholderId == null || placeholderId.isEmpty()) {
                continue;
            }
            String imageMarkdown = buildImageMarkdown(image);
            content = content.replace(placeholderId, imageMarkdown);
        }
        return content;
    }

    /**
     * 构建图片 Markdown 格式
     */
    private String buildImageMarkdown(ArticleState.ImageResult image) {
        String altText = image.getDescription() != null ? image.getDescription() : "image";
        return "\n![" + altText + "](" + image.getUrl() + ")\n";
    }
    /**
     * 调用 LLM（非流式）
     */
    private String callLlm(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 调用 LLM（流式输出，累积批量发送）
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType) {
        StringBuilder contentBuilder = new StringBuilder();
        ToolCallback[] toolCallbacks = ToolCallbacks.from(new WebCrawlerTool());
        ChatClient chatClient= ChatClient.builder(chatModel).defaultToolCallbacks(toolCallbacks).build();

        Flux<ChatResponse> streamResponse = chatClient.prompt(new Prompt(new UserMessage(prompt))).stream().chatResponse();

        // 累积 100ms 的内容后批量发送，减少消息频率
        streamResponse
                .buffer(Duration.ofMillis(200))
                .doOnNext(chunks -> {
                    String content = chunks.stream()
                            .map(response -> response.getResult().getOutput().getText())
                            .filter(text -> text != null && !text.isEmpty())
                            .collect(Collectors.joining());
                    if (!content.isEmpty()) {
                        contentBuilder.append(content);
                        streamHandler.accept(messageType.getStreamingPrefix() + content);
                    }
                })
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 解析 JSON 响应
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }
    /**
     * 构建配图结果
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId());
        return imageResult;
    }

    /**
     * 在章节标题后插入对应图片
     */
    private void insertImageAfterSection(StringBuilder fullContent,
                                         List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }
    }
    /**
     * 构建可用配图方式说明
     */
    private String buildAvailableMethodsDescription(List<String> enabledMethods) {
        // 如果为空或 null，表示支持所有方式
        if (enabledMethods == null || enabledMethods.isEmpty()) {
            return getAllMethodsDescription();
        }

        // 只描述允许的方式
        StringBuilder sb = new StringBuilder();
        for (String method : enabledMethods) {
            ImageMethodEnum methodEnum = ImageMethodEnum.getByValue(method);
            if (methodEnum != null && !methodEnum.isFallback()) {
                sb.append("   - ").append(methodEnum.getValue())
                        .append(": ").append(getMethodUsageDescription(methodEnum))
                        .append("\n");
            }
        }
        return sb.toString();
    }
    /**
     * 根据风格获取对应的 Prompt 附加内容
     */
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }

        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }

        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case GAME -> PromptConstant.STYLE_GAME_PROMPT;
            case CARTOON -> PromptConstant.STYLE_CARTOON_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }

    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle,
                                                             List<ArticleState.OutlineSection> currentOutline,
                                                             String modifySuggestion) {
        ThrowUtils.throwIf(currentOutline == null || currentOutline.isEmpty(),
                ErrorCode.OPERATION_ERROR, "当前大纲为空");
        String result = chatModel.call(new SystemMessage(PromptConstant.AI_MODIFY_OUTLINE_PROMPT),
                new UserMessage("请根据以下大纲，并修改建议，并返回修改后的大纲，请勿返回其他内容。"
                        + "主标题" + mainTitle + "副标题" + subTitle + "当前大纲" + currentOutline + "修改建议" + modifySuggestion));
        ArticleState.OutlineResult outlineResult = parseJsonResponse(result, ArticleState.OutlineResult.class, "大纲");
        return outlineResult.getSections();
    }
    /**
     * 阶段1：生成标题方案（3-5个）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase1_GenerateTitles(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题方案
            log.info("阶段1：开始生成标题方案, taskId={}", state.getTaskId());
            agent1GenerateTitleOptions(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            log.info("阶段1：标题方案生成完成, taskId={}, optionsCount={}",
                    state.getTaskId(), state.getTitleOptions().size());
        } catch (Exception e) {
            log.error("阶段1：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲（用户选择标题后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体2：生成大纲（流式输出）
            log.info("阶段2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            log.info("阶段2：大纲生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段2：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase3_GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体3：生成正文（流式输出）
            log.info("阶段3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("阶段3：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("阶段3：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("阶段3：开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("阶段3：正文生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段3：正文生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文生成失败: " + e.getMessage(), e);
        }
    }
    /**
     * 智能体1：生成标题方案（3-5个）
     */
    private void agent1GenerateTitleOptions(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", state.getTopic())
                + getStylePrompt(state.getStyle());

        String content = callLlm(prompt);
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){},
                "标题方案"
        );
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功, optionsCount={}", titleOptions.size());
    }

}