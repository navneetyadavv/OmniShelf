// AuditAction.java
package com.billing.model;

public enum AuditAction {
    OTP_REQUESTED,
    OTP_VERIFIED,
    OTP_FAILED,
    BILL_CREATED,
    BILL_CONFIRMED,
    BILL_CANCELLED,
    DISCOUNT_APPLIED,
    DISCOUNT_BLOCKED,       // staff tried to apply discount
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    RATE_LIMIT_HIT,
    PRICE_TAMPER_ATTEMPT    // price sent that doesn't match DB
}