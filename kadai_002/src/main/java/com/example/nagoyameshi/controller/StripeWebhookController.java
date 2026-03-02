package com.example.nagoyameshi.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.nagoyameshi.entity.User;
import com.example.nagoyameshi.repository.UserRepository;
import com.example.nagoyameshi.service.StripeService;
import com.example.nagoyameshi.service.SubscriptionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;

@RestController
public class StripeWebhookController {

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public StripeWebhookController(StripeService stripeService,
                                   SubscriptionService subscriptionService,
                                   UserRepository userRepository) {
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        // ここはデバッグ用（署名の全文は出さないほうが安全）
        System.out.println("=== webhook received ===");
        System.out.println("payloadLen=" + (payload == null ? "null" : payload.length()));
        System.out.println("Stripe-Signature header present: " + (sigHeader != null));
        if (sigHeader != null) {
            System.out.println("Stripe-Signature: " + sigHeader.substring(0, Math.min(sigHeader.length(), 80)) + "...");
        }

        if (payload == null || payload.isBlank()) {
            return ResponseEntity.badRequest().body("Empty payload");
        }

        if (sigHeader == null) {
            // Stripe CLI/Stripe本体からは必ず来る。来ないなら経路が違う
            return ResponseEntity.badRequest().body("Missing Stripe-Signature");
        }

        // 1) 署名検証して Event を復元
        final Event event;
        try {
            event = stripeService.constructEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            System.out.println("!!! signature verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            System.out.println("!!! failed to construct event: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to construct event");
        }

        System.out.println("eventType=" + event.getType() + ", eventId=" + event.getId());

        // 2) ハンドリング分岐
        try {
            switch (event.getType()) {

                // ----------------------------
                // 課金成功（Checkout完了）
                // ----------------------------
                case "checkout.session.completed" -> {
                    return handleCheckoutSessionCompleted(event);
                }

                // ----------------------------
                // 解約が確定した（期間末解約で期日到達 / 即時解約）
                // ※ Stripe側の仕様/設定で "customer.subscription.deleted" を使うのが一般的
                // ----------------------------
                case "customer.subscription.deleted" -> {
                    return handleSubscriptionDeleted(event);
                }

                // （任意）更新系も拾いたい場合：次回課金日やcancel予約の変化をDBに反映したい時
                // case "customer.subscription.updated" -> { ... }

                default -> {
                    // 他は無視してOK（200返すのがStripe的に優しい）
                    return ResponseEntity.ok("ignored");
                }
            }
        } catch (Exception e) {
            // 500を返すとStripe/Stripe CLIが再送してくれる（テスト中は便利）
            System.out.println("!!! webhook handler failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to process");
        }
    }

    // ----------------------------
    // checkout.session.completed
    // ----------------------------
    private ResponseEntity<String> handleCheckoutSessionCompleted(Event event) throws StripeException {

        Optional<StripeObject> objOpt = event.getDataObjectDeserializer().getObject();
        if (objOpt.isEmpty()) {
            return ResponseEntity.ok("No object");
        }

        StripeObject obj = objOpt.get();
        if (!(obj instanceof Session session)) {
            return ResponseEntity.ok("Not a checkout session");
        }

        // metadata から userId を取り出す
        String userIdStr = (session.getMetadata() != null) ? session.getMetadata().get("userId") : null;
        if (userIdStr == null) {
            return ResponseEntity.ok("No userId metadata");
        }

        final Integer userId;
        try {
            userId = Integer.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.ok("Invalid userId metadata");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok("User not found");
        }

        String subscriptionId = session.getSubscription();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return ResponseEntity.ok("No subscription id");
        }

        Subscription stripeSub = stripeService.retrieveSubscription(subscriptionId);
        StripeService.SubInfo info = stripeService.extractSubInfoFromStripe(stripeSub);

        // DB更新＆ROLE_PAID付与
        subscriptionService.activate(
                user,
                info.customerId(),
                info.subscriptionId(),
                info.currentPeriodEnd()
        );

        System.out.println("=== activate done for userId=" + userId + " sub=" + subscriptionId);
        return ResponseEntity.ok("ok");
    }

    // ----------------------------
    // customer.subscription.deleted
    // ----------------------------
    private ResponseEntity<String> handleSubscriptionDeleted(Event event) {

        Optional<StripeObject> objOpt = event.getDataObjectDeserializer().getObject();
        if (objOpt.isEmpty()) {
            return ResponseEntity.ok("No object");
        }

        StripeObject obj = objOpt.get();

        if (!(obj instanceof Subscription stripeSub)) {
            return ResponseEntity.ok("Not a subscription");
        }

        String stripeSubscriptionId = stripeSub.getId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return ResponseEntity.ok("No subscription id");
        }

        // DBを解約確定（ROLE_GENERALへ戻す）
        subscriptionService.finalizeCanceledByStripe(stripeSubscriptionId);

        System.out.println("=== canceled finalized for sub=" + stripeSubscriptionId);
        return ResponseEntity.ok("ok");
    }
}