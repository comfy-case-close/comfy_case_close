package com.fnbx.shared.logging;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** Request-scoped state; body advice and the fallback filter never duplicate the same event. */
public class ServletPayloadLogging {
    private static final String PREFIX = ServletPayloadLogging.class.getName();
    private final HttpPayloadLogger logger;
    public ServletPayloadLogging(HttpPayloadLogger logger) { this.logger = logger; }
    public boolean active() { return logger.active(); }
    private PayloadRedactor redactor(HttpServletRequest request) {
        Object existing = request.getAttribute(PREFIX + ".redactor");
        if (existing instanceof PayloadRedactor redactor) return redactor;
        PayloadRedactor created = logger.newRedactor();
        request.setAttribute(PREFIX + ".redactor", created);
        return created;
    }
    private Map<String, Object> metadata(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", request.getMethod());
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        data.put("route", route == null ? "<unmapped>" : route.toString());
        return data;
    }
    public void request(HttpServletRequest request, Object body) {
        if (!active() || request.getAttribute(PREFIX + ".request") != null) return;
        request.setAttribute(PREFIX + ".request", true);
        try {
            Map<String, Object> data = metadata(request);
            Map<String, Object> headers = new LinkedHashMap<>();
            Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, Collections.list(request.getHeaders(name))));
            data.put("headers", headers);
            data.put("parameters", request.getParameterMap());
            if (request instanceof MultipartHttpServletRequest multipart) {
                List<Object> files = new ArrayList<>();
                multipart.getMultiFileMap().forEach((field, parts) -> parts.forEach(file -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("field", field); entry.put("fileName", file.getOriginalFilename());
                    entry.put("contentType", file.getContentType()); entry.put("sizeBytes", file.getSize());
                    files.add(entry);
                }));
                data.put("files", files);
            }
            logger.write("request", data, body, redactor(request));
        } catch (RuntimeException failure) { logger.omitted("request"); }
    }
    public void response(HttpServletRequest request, HttpServletResponse response, Object body) {
        if (!active() || request.getAttribute(PREFIX + ".response") != null) return;
        request.setAttribute(PREFIX + ".response", true);
        try {
            Map<String, Object> data = metadata(request);
            data.put("status", response.getStatus());
            Map<String, Object> headers = new LinkedHashMap<>();
            response.getHeaderNames().forEach(name -> headers.put(name, response.getHeaders(name)));
            data.put("headers", headers);
            logger.write("response", data, body, redactor(request));
        } catch (RuntimeException failure) { logger.omitted("response"); }
    }
    public void fallback(HttpServletRequest request, HttpServletResponse response) {
        if (!active()) return;
        request(request, Map.of("omitted", "not_consumed_as_json", "sizeBytes", request.getContentLengthLong()));
        response(request, response, Map.of("omitted", "not_captured_as_json", "contentType",
                Objects.toString(response.getContentType(), "unknown")));
    }
}
