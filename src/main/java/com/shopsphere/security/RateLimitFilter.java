package com.shopsphere.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** In-memory sliding-window limiter per client IP + route group. Good for a single instance; use Redis if you scale out. */
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** @return limit per minute for the path, or 0 when unlimited. */
    static int limitFor(String path, String method) {
        if (path.equals("/api/auth/login")) return 10;
        if (path.startsWith("/api/auth/") || path.startsWith("/login/oauth2") || path.startsWith("/oauth2/")) return 20;
        if (path.startsWith("/api/chatbot")) return 20;
        if (path.equals("/api/sellers/me/uploads")) return 20;
        return 0;
    }

    boolean allow(String key, int limit, long nowMs) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && nowMs - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(nowMs);
            return true;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        int limit = limitFor(req.getRequestURI(), req.getMethod());
        if (limit > 0 && !allow(req.getRemoteAddr() + "|" + req.getRequestURI(), limit, System.currentTimeMillis())) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
