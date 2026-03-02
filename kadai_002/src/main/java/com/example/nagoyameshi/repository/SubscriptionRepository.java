package com.example.nagoyameshi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nagoyameshi.entity.Subscription;
import com.example.nagoyameshi.entity.User;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {
    Optional<Subscription> findByUser(User user);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}