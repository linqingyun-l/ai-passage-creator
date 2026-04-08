package org.example.aipassagecreator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aipassagecreator.service.PaymentRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe Webhook 控制器
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
@Hidden
public class StripeWebhookController {

    @Resource
    private PaymentRecordService paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理 Stripe Webhook 回调
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("收到 Stripe Webhook 请求");

        try {
            // 验证 Webhook 签名
            Event event = paymentService.constructEvent(payload, sigHeader);

            log.info("收到 Stripe Webhook 事件, type={}, id={}", event.getType(), event.getId());

            // 处理事件
            switch (event.getType()) {
                case "checkout.session.completed":
                    log.info("处理 checkout.session.completed 事件");
                    // 使用 event.getData().getObject() 而不是 getDataObjectDeserializer()
                    Session session = (Session) event.getData().getObject();
                    if (session == null) {
                        log.error("无法解析 Session 对象，尝试手动解析");
                        throw new RuntimeException("无法解析 Session 对象");
                    }
                    log.info("Session 解析成功, sessionId={}, paymentIntent={}", session.getId(), session.getPaymentIntent());
                    paymentService.handlePaymentSuccess(session);
                    break;

                case "checkout.session.async_payment_succeeded":
                    log.info("处理 checkout.session.async_payment_succeeded 事件");
                    Session asyncSession = (Session) event.getData().getObject();
                    if (asyncSession == null) {
                        log.error("无法解析异步支付 Session 对象");
                        throw new RuntimeException("无法解析 Session 对象");
                    }
                    paymentService.handlePaymentSuccess(asyncSession);
                    break;

                default:
                    log.info("未处理的事件类型: {}", event.getType());
                    break;
            }

            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("处理 Stripe Webhook 失败: {}", e.getMessage(), e);
            // 返回 500 让 Stripe 重试
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }
}