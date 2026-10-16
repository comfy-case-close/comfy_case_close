package com.fnbx.shared.logging;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.slf4j.*;

/** Shared JSON formatting/policy. Never logs raw content when conversion fails. */
public class HttpPayloadLogger {
    private static final Logger log = LoggerFactory.getLogger(HttpPayloadLogger.class);
    private final ObjectMapper mapper;
    private final PayloadLoggingProperties properties;

    public HttpPayloadLogger(ObjectMapper mapper, PayloadLoggingProperties properties) {
        var binary = new com.fasterxml.jackson.databind.module.SimpleModule();
        binary.addSerializer(byte[].class, new com.fasterxml.jackson.databind.ser.std.StdSerializer<byte[]>(byte[].class) {
            @Override public void serialize(byte[] bytes, com.fasterxml.jackson.core.JsonGenerator output,
                    SerializerProvider provider) throws java.io.IOException {
                output.writeStartObject(); output.writeStringField("omitted", "binary");
                output.writeNumberField("sizeBytes", bytes.length); output.writeEndObject();
            }
        });
        binary.addSerializer(org.springframework.core.io.Resource.class,
                new com.fasterxml.jackson.databind.ser.std.StdSerializer<org.springframework.core.io.Resource>(org.springframework.core.io.Resource.class) {
            @Override public void serialize(org.springframework.core.io.Resource resource, com.fasterxml.jackson.core.JsonGenerator output,
                    SerializerProvider provider) throws java.io.IOException { output.writeString("[BINARY OMITTED]"); }
        });
        this.mapper = mapper.copy().registerModule(binary);
        this.properties = properties;
    }
    public boolean active() { return properties.isEnabled() && log.isDebugEnabled(); }
    public PayloadRedactor newRedactor() { return new PayloadRedactor(properties.getAdditionalSensitiveFields()); }

    public void write(String direction, Map<String, Object> metadata, Object body, PayloadRedactor redactor) {
        if (!active()) return;
        try {
            ObjectNode event = mapper.valueToTree(metadata);
            JsonNode content;
            try { content = body instanceof String text ? mapper.readTree(text) : mapper.valueToTree(body); }
            catch (Exception unsafe) { content = mapper.createObjectNode().put("omitted", "unreadable_or_unserializable"); }
            event.set("body", content == null ? NullNode.instance : content);
            String sanitized = mapper.writeValueAsString(redactor.redact(event));
            int limit = "response".equals(direction) ? properties.getMaxResponseChars() : properties.getMaxRequestChars();
            boolean truncated = !("response".equals(direction) && properties.isFullResponse()) && sanitized.length() > limit;
            String shown = truncated ? sanitized.substring(0, safeEnd(sanitized, limit)) : sanitized;
            log.debug("HTTP payload direction={} truncated={} chars={} payload={}", direction, truncated, sanitized.length(), shown);
        } catch (Exception failure) {
            // Logging must not change the response or expose converter exception text.
            log.debug("HTTP payload direction={} omitted=serialization_failed", direction);
        }
    }

    public void omitted(String direction) {
        if (active()) log.debug("HTTP payload direction={} omitted=metadata_unavailable", direction);
    }

    private static int safeEnd(String text, int limit) {
        return limit > 0 && Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
    }
}
