package com.fnbx.mail;

/** Delivery failure classification reused from Vakot_BE. */
public class MailDeliveryException extends RuntimeException {

    private final boolean retryable;
    private final boolean providerFault;

    private MailDeliveryException(String message, boolean retryable, boolean providerFault) {
        super(message);
        this.retryable = retryable;
        this.providerFault = providerFault;
    }

    /** The provider or the network might succeed on another attempt: a timeout, a 5xx, a rate limit. */
    public static MailDeliveryException retryable(String message) {
        return new MailDeliveryException(message, true, false);
    }

    /** No number of attempts will change the answer: a bad key, an unverified sender, a malformed address. */
    public static MailDeliveryException providerMisconfigured(String message) {
        return new MailDeliveryException(message, false, true);
    }

    public static MailDeliveryException rejectedMessage(String message) {
        return new MailDeliveryException(message, false, false);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isProviderFault() {
        return providerFault;
    }
}
