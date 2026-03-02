package com.example.nagoyameshi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.nagoyameshi.Security.UserDetailsImpl;
import com.example.nagoyameshi.entity.User;
import com.example.nagoyameshi.service.StripeService;
import com.example.nagoyameshi.service.SubscriptionService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

@Controller
@RequestMapping("/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(StripeService stripeService,
                                  SubscriptionService subscriptionService) {
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl userDetails,
                        Model model) {
        User user = userDetails.getUser();
        model.addAttribute("user", user);
        return "subscription/index";
    }

    @GetMapping("/purchase")
    public String purchasePage() {
        return "subscription/purchase";
    }

    @PostMapping("/checkout")
    public String checkout(@AuthenticationPrincipal UserDetailsImpl userDetails,
                           RedirectAttributes redirectAttributes) {
        Integer userId = userDetails.getUser().getId();

        try {
            Session session = stripeService.createCheckoutSession(userId);
            return "redirect:" + session.getUrl();
        } catch (StripeException e) {
            log.error("Stripe checkout start failed. message={}, type={}, code={}, requestId={}",
                    e.getMessage(), e.getClass().getName(), e.getCode(), e.getRequestId(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "決済開始に失敗しました。もう一度お試しください。");
            return "redirect:/subscription/purchase";
        }
    }

    @GetMapping("/success")
    public String success(Model model) {
        model.addAttribute("successMessage", "決済が完了しました。反映まで少し時間がかかる場合があります。必要なら再ログインしてください。");
        return "subscription/success";
    }

    @GetMapping("/cancel")
    public String cancelPage(Model model) {
        model.addAttribute("errorMessage", "決済をキャンセルしました。");
        return "subscription/cancel";
    }

    // ★解約予約：POST /subscription/cancel
    @PostMapping("/cancel")
    public String cancel(@AuthenticationPrincipal UserDetailsImpl userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.requestCancelAtPeriodEnd(userDetails.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "解約を受け付けました。期間満了で解約されます。");
        } catch (Exception e) {
            log.error("Cancel request failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "解約に失敗しました。もう一度お試しください。");
        }
        return "redirect:/subscription";
    }
}