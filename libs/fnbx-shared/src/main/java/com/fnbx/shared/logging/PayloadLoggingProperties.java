package com.fnbx.shared.logging;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fnb.logging.payload")
public class PayloadLoggingProperties {
    private boolean enabled = true;
    private boolean fullResponse;
    private int maxRequestChars = 16384;
    private int maxResponseChars = 16384;
    private Set<String> additionalSensitiveFields = Set.of();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isFullResponse() { return fullResponse; }
    public void setFullResponse(boolean value) { fullResponse = value; }
    public int getMaxRequestChars() { return maxRequestChars; }
    public void setMaxRequestChars(int value) { maxRequestChars = positive(value); }
    public int getMaxResponseChars() { return maxResponseChars; }
    public void setMaxResponseChars(int value) { maxResponseChars = positive(value); }
    public Set<String> getAdditionalSensitiveFields() { return additionalSensitiveFields; }
    public void setAdditionalSensitiveFields(Set<String> value) { additionalSensitiveFields = Set.copyOf(value); }
    private static int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Payload character limit must be positive");
        return value;
    }
}
