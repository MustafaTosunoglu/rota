package com.rota.proxy.internal;

/** Raised when the tenant's daily Free-tier Try It limit is reached → HTTP 429. */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(int limit) {
        super("Daily Try It limit reached (" + limit + "). Try again tomorrow.");
    }
}
