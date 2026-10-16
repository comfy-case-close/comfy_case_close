package com.fnbx.shared.logging;

import java.util.UUID;

/** Shared correlation header policy for servlet services and the reactive gateway. */
public final class RequestIds {
    public static final String HEADER = "X-Request-ID";
    private RequestIds() {}

    public static String resolve(String incoming) {
        return incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
    }
}
