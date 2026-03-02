package com.example.nagoyameshi.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nagoyameshi.entity.Role;
import com.example.nagoyameshi.entity.Subscription;
import com.example.nagoyameshi.entity.SubscriptionStatus;
import com.example.nagoyameshi.entity.User;
import com.example.nagoyameshi.repository.RoleRepository;
import com.example.nagoyameshi.repository.SubscriptionRepository;
import com.example.nagoyameshi.repository.UserRepository;
import com.stripe.exception.StripeException; 

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               RoleRepository roleRepository,
                               UserRepository userRepository,
                               StripeService stripeService) {
        this.subscriptionRepository = subscriptionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.stripeService = stripeService;
    }

    @Transactional
    public void activate(User user,
                         String stripeCustomerId,
                         String stripeSubscriptionId,
                         LocalDateTime currentPeriodEnd) {

        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseGet(() -> {
                    Subscription s = new Subscription();
                    s.setUser(user);
                    s.setStatus(SubscriptionStatus.INACTIVE);
                    return s;
                });

        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeCustomerId(stripeCustomerId);
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        sub.setCurrentPeriodEnd(currentPeriodEnd);
        sub.setStartedAt(LocalDateTime.now());
        sub.setEndAt(null);

        subscriptionRepository.save(sub);

        Role paidRole = roleRepository.findByName("ROLE_PAID")
                .orElseThrow(() -> new IllegalStateException("rolesテーブルに ROLE_PAID が存在しません"));

        user.setRole(paidRole);
        userRepository.save(user);
    }

    /**
     * ★期間末解約を予約する（Stripeも更新する）
     * - Stripe: cancel_at_period_end=true
     * - DB: status=CANCEL_REQUESTED, endAt=currentPeriodEnd
     * - 役割: 期間末まではROLE_PAID維持
     */
    public void requestCancelAtPeriodEnd(User user) throws StripeException {

        // DBのサブスク情報がないなら解約できない
        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("Subscription が存在しません"));

        if (sub.getStripeSubscriptionId() == null || sub.getStripeSubscriptionId().isBlank()) {
            throw new IllegalStateException("Stripe subscription id が保存されていません");
        }

        // まずStripeを更新（失敗したらDBは触らない）
        com.stripe.model.Subscription updatedStripeSub =
                stripeService.requestCancelAtPeriodEnd(sub.getStripeSubscriptionId());

        StripeService.SubInfo info = stripeService.extractSubInfoFromStripe(updatedStripeSub);

        // 次にDB更新（トランザクション）
        requestCancelDbOnly(user, info.currentPeriodEnd());
    }

    @Transactional
    protected void requestCancelDbOnly(User user, LocalDateTime currentPeriodEnd) {
        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("Subscription が存在しません"));

        if (sub.getStatus() == SubscriptionStatus.CANCELED || sub.getStatus() == SubscriptionStatus.INACTIVE) {
            return;
        }

        sub.setStatus(SubscriptionStatus.CANCEL_REQUESTED);
        sub.setEndAt(currentPeriodEnd);           // 期間末
        sub.setCurrentPeriodEnd(currentPeriodEnd);

        subscriptionRepository.save(sub);
    }

    /**
     * ★Webhook用：Stripe側でsubscriptionが終了したタイミングで確定させる
     * - status=CANCELED
     * - endAt=now（またはStripe current_period_endでもOK）
     * - role=ROLE_GENERAL
     */
    @Transactional
    public void finalizeCanceledByStripe(String stripeSubscriptionId) {
        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> new IllegalStateException("DBに該当Stripe Subscriptionが存在しません: " + stripeSubscriptionId));

        if (sub.getStatus() == SubscriptionStatus.CANCELED) return;

        sub.setStatus(SubscriptionStatus.CANCELED);
        sub.setEndAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        User user = sub.getUser();

        Role generalRole = roleRepository.findByName("ROLE_GENERAL")
                .orElseThrow(() -> new IllegalStateException("rolesテーブルに ROLE_GENERAL が存在しません"));

        user.setRole(generalRole);
        userRepository.save(user);
    }
}