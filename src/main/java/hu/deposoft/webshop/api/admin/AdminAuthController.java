package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.customer.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin session login for the SPA (T15): cookie-session auth, ADMIN role required.
 *
 * <p>CSRF precondition: the login POST is CSRF-protected. The SPA must first issue a
 * GET (to any endpoint) to obtain the {@code XSRF-TOKEN} cookie, then echo it back in
 * the {@code X-XSRF-TOKEN} header on the login POST. Without that header the POST is
 * rejected before reaching this controller.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final AdminProperties adminProperties;

    public record LoginRequest(String email, String password) {
    }

    public record MeResponse(String email, String role, boolean productEditorEnabled) {
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@RequestBody LoginRequest req,
                                            HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAdmin = auth.getAuthorities()
                .contains(new SimpleGrantedAuthority(CustomerRole.ADMIN.authority()));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Anti session-fixation: rotate the session id once the principal is authenticated,
        // mirroring what form login does automatically (we authenticate manually here).
        request.getSession();        // ensure a session exists
        request.changeSessionId();   // rotate id on authentication
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
        contextRepository.saveContext(securityContext, request, response);
        return ResponseEntity.ok(new MeResponse(auth.getName(), "ADMIN", adminProperties.productEditorEnabled()));
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName(), "ADMIN", adminProperties.productEditorEnabled());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
