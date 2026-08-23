package com.comfy.caseclose.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

final class LoggingValueSanitizer {

    private static final int MAX_STRING_LENGTH = 180;
    private static final int MAX_FIELDS = 14;

    private LoggingValueSanitizer() {
    }

    static String summarizeArguments(String[] parameterNames, Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < args.length; i++) {
            String name = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            joiner.add(name + "=" + summarizeValue(name, args[i], 0));
        }
        return joiner.toString();
    }

    static String summarizeResult(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            String bodySummary = body == null ? "null" : body.getClass().getSimpleName();
            return "ResponseEntity{status=" + responseEntity.getStatusCode().value() + ", body=" + bodySummary + "}";
        }
        return summarizeValue("result", result, 1);
    }

    static String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return truncate(message.replaceAll("(?i)(passcode|password|token|secret|authorization)=([^,\\s}]+)", "$1=***"));
    }

    static boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("passcode")
                || lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("credential")
                || lower.contains("hash");
    }

    private static String summarizeValue(String name, Object value, int depth) {
        if (value == null) {
            return "null";
        }
        if (isSensitiveName(name)) {
            return "***";
        }
        if (isSimpleValue(value)) {
            return formatSimple(value);
        }
        if (value instanceof ServletRequest || value instanceof ServletResponse) {
            return value.getClass().getSimpleName();
        }
        if (value instanceof Collection<?> collection) {
            return value.getClass().getSimpleName() + "{size=" + collection.size() + "}";
        }
        if (value instanceof Map<?, ?> map) {
            return value.getClass().getSimpleName() + "{size=" + map.size() + "}";
        }
        if (value instanceof Page<?> page) {
            return "Page{number=" + page.getNumber() + ", size=" + page.getSize()
                    + ", totalElements=" + page.getTotalElements() + "}";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[]";
        }
        if (depth > 0 || !isApplicationDto(value)) {
            return value.getClass().getSimpleName();
        }
        return summarizeBean(value);
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof Temporal;
    }

    private static String formatSimple(Object value) {
        if (value instanceof CharSequence sequence) {
            return "\"" + truncate(sequence.toString()) + "\"";
        }
        return String.valueOf(value);
    }

    private static String summarizeBean(Object value) {
        StringJoiner joiner = new StringJoiner(", ", value.getClass().getSimpleName() + "{", "}");
        int count = 0;
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null) {
                    continue;
                }
                if (count++ >= MAX_FIELDS) {
                    joiner.add("...");
                    break;
                }
                String propertyName = descriptor.getName();
                Object propertyValue = readProperty(value, descriptor);
                joiner.add(propertyName + "=" + summarizeValue(propertyName, propertyValue, 1));
            }
        } catch (IntrospectionException ex) {
            return value.getClass().getSimpleName();
        }
        return joiner.toString();
    }

    private static Object readProperty(Object value, PropertyDescriptor descriptor) {
        try {
            return descriptor.getReadMethod().invoke(value);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return "<unreadable>";
        }
    }

    private static boolean isApplicationDto(Object value) {
        Package valuePackage = value.getClass().getPackage();
        if (valuePackage == null) {
            return false;
        }
        String packageName = valuePackage.getName();
        return packageName.startsWith("com.comfy.caseclose.dto.request");
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_STRING_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STRING_LENGTH) + "...";
    }
}
