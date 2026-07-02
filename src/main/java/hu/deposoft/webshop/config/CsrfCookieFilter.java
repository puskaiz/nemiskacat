package hu.deposoft.webshop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Renders the CSRF token so {@code CookieCsrfTokenRepository} writes the
 * {@code XSRF-TOKEN} cookie on the response. Spring Security 6/7 loads the token
 * lazily, so a pure-JSON client (the admin SPA) would never receive the cookie
 * to echo back as {@code X-XSRF-TOKEN}. Server-rendered pages materialise it via
 * the Thymeleaf form field; the SPA relies on this filter instead.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // touching the token triggers the cookie write
        }
        filterChain.doFilter(request, response);
    }
}
