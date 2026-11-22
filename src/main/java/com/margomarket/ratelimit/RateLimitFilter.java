package com.margomarket.ratelimit;

import com.margomarket.user.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs inside the security chain after JWT authentication, so per-user limits know the caller.
 * Deliberately not a bean: Spring Boot would also register it as a plain servlet filter.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limiter.enabled() || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = auth != null && auth.getPrincipal() instanceof User u ? u : null;
        boolean admin = user != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        for (RateLimitPolicy policy : RateLimitPolicies.match(request.getMethod(), path)) {
            if (admin && policy.adminExempt()) continue;
            String key = policy.key() == RateLimitPolicy.Key.IP ? request.getRemoteAddr()
                    : user != null ? String.valueOf(user.getId()) : null;
            if (key == null) continue;
            long wait = limiter.tryConsume(policy, key);
            if (wait > 0) {
                reject(response, new RateLimitExceededException(wait));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, RateLimitExceededException exception) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(exception.getRetryAfterSeconds()));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), exception.response());
    }
}
