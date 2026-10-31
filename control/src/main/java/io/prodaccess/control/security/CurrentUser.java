package io.prodaccess.control.security;

import io.prodaccess.control.config.ProdAccessProperties;
import io.prodaccess.control.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identity of the caller. Placeholder for the SSO/OIDC integration: the proxy in front authenticates and
 * forwards the user in {@code X-User}. Authorization (who may do what) lives in the separate permissions system.
 */
@Component
public class CurrentUser extends OncePerRequestFilter {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();
    private final ProdAccessProperties props;

    CurrentUser(ProdAccessProperties props) {
        this.props = props;
    }

    public static String get() {
        String u = USER.get();
        if (u == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
        return u;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String user = req.getHeader("X-User");
        if (user == null || user.isBlank()) user = props.devUser();
        if (user == null || user.isBlank()) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "not authenticated");
            return;
        }
        USER.set(user.trim());
        try {
            chain.doFilter(req, res);
        } finally {
            USER.remove();
        }
    }
}
