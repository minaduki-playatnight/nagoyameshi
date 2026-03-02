package com.example.nagoyameshi.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;

@Service
public class StripeService {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    // Checkoutで使う priceId（StripeのPrice ID）
    @Value("${stripe.price.id}")
    private String priceId;

    public StripeService(@Value("${stripe.secret.key}") String secretKey) {
        Stripe.apiKey = secretKey;
    }

    public Event constructEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    // =========================
    // Checkout Session 作成（subscription）
    // =========================
    public Session createCheckoutSession(Integer userId) throws StripeException {

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", String.valueOf(userId));

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl("http://localhost:8080/subscription/success")
                        .setCancelUrl("http://localhost:8080/subscription/cancel")
                        .putAllMetadata(metadata)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPrice(priceId)
                                        .build()
                        )
                        .build();

        return Session.create(params);
    }

    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    // =========================
    // 期間末解約（cancel_at_period_end = true）
    // =========================
    public Subscription requestCancelAtPeriodEnd(String subscriptionId) throws StripeException {

        SubscriptionUpdateParams params =
                SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true)
                        .build();

        Subscription sub = Subscription.retrieve(subscriptionId);
        return sub.update(params);
    }

    // =========================
    // Stripe Subscription から必要情報を抜く
    // =========================
    public SubInfo extractSubInfoFromStripe(Subscription stripeSub) {

        String customerId = stripeSub.getCustomer();
        String subscriptionId = stripeSub.getId();

        // Stripe Java SDKのバージョン差で Subscription直下の getCurrentPeriodEnd() が無いことがある
        // → items.data[0].current_period_end を使うのが安定
        Long endEpochSec = null;

        try {
            if (stripeSub.getItems() != null
                    && stripeSub.getItems().getData() != null
                    && !stripeSub.getItems().getData().isEmpty()
                    && stripeSub.getItems().getData().get(0) != null) {
                endEpochSec = stripeSub.getItems().getData().get(0).getCurrentPeriodEnd();
            }
        } catch (Exception ignored) {
        }

        LocalDateTime currentPeriodEnd = null;
        if (endEpochSec != null) {
            currentPeriodEnd = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(endEpochSec),
                    ZoneId.systemDefault()
            );
        }

        return new SubInfo(customerId, subscriptionId, currentPeriodEnd);
    }

    public record SubInfo(String customerId, String subscriptionId, LocalDateTime currentPeriodEnd) {}
}