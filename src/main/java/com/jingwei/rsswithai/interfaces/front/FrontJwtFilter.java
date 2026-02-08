package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.interfaces.context.UserContext;
import com.jingwei.rsswithai.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.List;

@Component
public class FrontJwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            new PublicEndpoint("POST", "/api/login"),
            new PublicEndpoint("POST", "/api/register"),
            new PublicEndpoint("POST", "/api/refresh"),
            new PublicEndpoint("GET", "/api/front/v1/rss-sources"),
            new PublicEndpoint("GET", "/api/front/v1/trends/wordcloud"),
            new PublicEndpoint("GET", "/api/front/v1/trends/hotevents"),
            new PublicEndpoint("GET", "/api/front/v1/articles/search"),
            new PublicEndpoint("GET", "/api/front/v1/articles/source/**"),
            new PublicEndpoint("GET", "/api/front/v1/articles/*"),
            new PublicEndpoint("GET", "/api/front/v1/articles/*/extra"),
            new PublicEndpoint("GET", "/api/front/v1/articles/*/recommendations")
    );

    public FrontJwtFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }

        // Only protect /api/** (excluding admin api) like previous interceptor
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (path.startsWith("/api/admin/")) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isPublic = isPublicEndpoint(request);
        String token = extractBearerToken(request);
        if (token == null) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!jwtUtils.validateToken(token)) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Long userId = jwtUtils.extractUserId(token);
        String username = jwtUtils.extractUsername(token);

        if (userId == null || username == null || username.isBlank()) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ScopedValue.where(UserContext.USER_ID, userId)
                .where(UserContext.USERNAME, username)
                .run(() -> {
                    try {
                        filterChain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null || method == null) {
            return false;
        }

        return PUBLIC_ENDPOINTS.stream().anyMatch(rule -> rule.matches(method, path));
    }

    private record PublicEndpoint(String method, String pattern) {
        private boolean matches(String requestMethod, String path) {
            return method.equalsIgnoreCase(requestMethod) && PATH_MATCHER.match(pattern, path);
        }
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            return null;
        }
        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}