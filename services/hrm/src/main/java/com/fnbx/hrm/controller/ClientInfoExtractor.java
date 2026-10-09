package com.fnbx.hrm.controller;

import com.fnbx.hrm.service.confirmation.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

final class ClientInfoExtractor {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientInfoExtractor() {}

    static ClientInfo from(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return new ClientInfo(ip, request.getHeader("User-Agent"));
    }
}
