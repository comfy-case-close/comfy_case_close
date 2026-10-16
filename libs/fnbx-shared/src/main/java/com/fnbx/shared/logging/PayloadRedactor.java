package com.fnbx.shared.logging;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** Redacts before truncation. A per-request instance also removes known secrets echoed in other fields. */
public final class PayloadRedactor {
    private static final String MASK = "[REDACTED]";
    private static final Pattern QUERY = Pattern.compile("([?&])([^=&#\\s]+)=([^&#\\s]*)");
    private static final Pattern TOKEN_PATH = Pattern.compile("(/(?:public/payslip-confirmations|payslip-confirm)/)[^/?#\\s]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private final Set<String> extra;
    private final Set<String> secrets = new HashSet<>();

    public PayloadRedactor(Set<String> additionalFields) {
        extra = new HashSet<>();
        additionalFields.forEach(field -> extra.add(normalize(field)));
    }

    private static String normalize(String key) { return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private boolean sensitive(String key) {
        String n = normalize(key);
        return extra.contains(n) || n.contains("password") || n.contains("passcode") || n.contains("token")
                || n.contains("secret") || n.contains("authorization") || n.contains("apikey")
                || n.contains("platformkey") || n.contains("credential") || n.endsWith("signature")
                || n.equals("otp") || n.endsWith("otp") || n.equals("onetimepassword") || n.equals("pwd")
                || n.equals("cookie") || n.equals("setcookie") || n.equals("key") || n.equals("proof");
    }

    public JsonNode redact(JsonNode source) {
        collect(source);
        return copy(source);
    }

    private void remember(JsonNode value) {
        if (value.isValueNode() && !value.isNull()) {
            String text = value.asText();
            if (!text.isEmpty()) secrets.add(text);
            if (text.regionMatches(true, 0, "Bearer ", 0, 7)) secrets.add(text.substring(7));
        } else value.forEach(this::remember);
    }

    private void collect(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            if (sensitive(field.getKey())) remember(field.getValue());
            else collect(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::collect);
    }

    private static boolean binary(String key) {
        String n = normalize(key);
        return n.contains("base64") || n.equals("binary") || n.equals("bytes") || n.equals("filecontent");
    }

    private JsonNode copy(JsonNode node) {
        if (node.isBinary()) return TextNode.valueOf("[BINARY OMITTED]");
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(field -> out.set(field.getKey(), sensitive(field.getKey())
                    ? TextNode.valueOf(MASK) : binary(field.getKey()) ? TextNode.valueOf("[BINARY OMITTED]") : copy(field.getValue())));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> out.add(copy(value)));
            return out;
        }
        if (node.isTextual()) return TextNode.valueOf(scrub(node.textValue()));
        return node.deepCopy();
    }

    private String scrub(String value) {
        String out = TOKEN_PATH.matcher(value).replaceAll("$1" + MASK);
        out = BEARER.matcher(out).replaceAll("Bearer " + MASK);
        out = JWT.matcher(out).replaceAll(MASK);
        var matcher = QUERY.matcher(out);
        StringBuilder replaced = new StringBuilder();
        while (matcher.find()) {
            String name;
            try { name = java.net.URLDecoder.decode(matcher.group(2), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { name = "token"; }
            if (sensitive(name)) matcher.appendReplacement(replaced,
                    java.util.regex.Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + "=" + MASK));
            else matcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(replaced);
        out = replaced.toString();
        // Longest first avoids partially masking a secret that contains another secret.
        for (String secret : secrets.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            out = out.replace(secret, MASK);
        }
        return out;
    }
}
