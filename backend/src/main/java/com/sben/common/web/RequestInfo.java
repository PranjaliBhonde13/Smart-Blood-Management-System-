package com.sben.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client details of the current HTTP request (null outside a request, e.g. in
 * scheduled jobs). The IP is the real client address: server.forward-headers-strategy
 * makes Tomcat honour X-Forwarded-For from the reverse proxy.
 */
public final class RequestInfo {

    private RequestInfo() {}

    public static String clientIp() {
        HttpServletRequest r = current();
        return r == null ? null : r.getRemoteAddr();
    }

    public static String userAgent() {
        HttpServletRequest r = current();
        return r == null ? null : r.getHeader("User-Agent");
    }

    private static HttpServletRequest current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }
}
