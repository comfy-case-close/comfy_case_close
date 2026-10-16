package com.fnbx.shared.logging;

import java.lang.reflect.Type;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

@RestControllerAdvice
public class PayloadBodyAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {
    private final ServletPayloadLogging logging;
    public PayloadBodyAdvice(ServletPayloadLogging logging) { this.logging = logging; }
    private static boolean application(MethodParameter parameter) {
        return parameter.getContainingClass().getPackageName().startsWith("com.fnbx.");
    }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return logging.active() && application(parameter);
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
            Type type, Class<? extends HttpMessageConverter<?>> converter) {
        recordRequest(body, input.getHeaders().getContentType());
        return body;
    }
    @Override public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter,
            Type type, Class<? extends HttpMessageConverter<?>> converter) {
        recordRequest(null, input.getHeaders().getContentType());
        return body;
    }
    private void recordRequest(Object body, MediaType mediaType) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            logging.request(attributes.getRequest(), json(mediaType) && !(body instanceof byte[])
                    && !(body instanceof Resource) ? body : omitted(body, mediaType));
        }
    }
    @Override public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return logging.active() && application(parameter);
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest input && response instanceof ServletServerHttpResponse output) {
            // Also covers unreadable request bodies handled before afterBodyRead can run.
            logging.request(input.getServletRequest(), Map.of("omitted", "no_json_body_read"));
            Object recorded = body instanceof byte[] || body instanceof Resource || !json(contentType)
                    ? omitted(body, contentType) : body;
            logging.response(input.getServletRequest(), output.getServletResponse(), recorded);
        }
        return body;
    }
    static boolean json(MediaType type) {
        return type != null && ("json".equalsIgnoreCase(type.getSubtype()) || type.getSubtype().endsWith("+json"));
    }
    private static Map<String, Object> omitted(Object body, MediaType type) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("omitted", "non_json_or_binary");
        metadata.put("contentType", type == null ? "unknown" : type.toString());
        if (body instanceof byte[] bytes) metadata.put("sizeBytes", bytes.length);
        if (body instanceof Resource resource) metadata.put("fileName", resource.getFilename());
        // Never call Resource.contentLength(): InputStreamResource may consume the download stream.
        return metadata;
    }
}
