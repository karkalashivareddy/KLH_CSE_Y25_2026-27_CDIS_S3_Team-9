package com.cloudresilience.modules.orders.domain;

public enum PaymentStatus {
    PROCESSING,
    SUCCEEDED,
    DECLINED,
    TIMED_OUT,
    FAILED,
    VOIDED
}
