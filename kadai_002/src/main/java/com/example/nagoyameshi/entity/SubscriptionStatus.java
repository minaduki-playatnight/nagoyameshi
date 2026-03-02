package com.example.nagoyameshi.entity;


public enum SubscriptionStatus {
    INACTIVE,
    ACTIVE,
    CANCEL_REQUESTED,  // ★追加（期間末解約予約）
    CANCELED
}