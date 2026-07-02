package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.api.CartController;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.integrations.wordpress.WordPressPasswordEncoder;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Duration;
import java.util.Map;

/**
 * Security: cookie double-submit CSRF (T8) + customer form login (T13).
 *
 * Passwords use a delegating encoder — new/upgraded ones are {bcrypt}; migrated
 * WooCommerce customers carry {wp} phpass hashes that upgrade to bcrypt on first
 * login (UserDetailsPasswordService). Catalog/cart/checkout stay permitAll; the
 * cacheable pages must remain cookie-free (guarded by the byte-identical test).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                "wp", new WordPressPasswordEncoder()));
        // legacy migrated rows are bare {wp}; nothing else should be unprefixed
        encoder.setDefaultPasswordEncoderForMatches(new WordPressPasswordEncoder());
        return encoder;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Shared with {@code AdminAuthController} so the manual login persists the context the
     * same way the filter chain reads it back — a private {@code new} could silently diverge.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationSuccessHandler cartMergeSuccessHandler) throws Exception {
        CsrfTokenRequestAttributeHandler plainHandler = new CsrfTokenRequestAttributeHandler();
        http
                .csrf(csrf -> csrf
                        // the bank POSTs its signed return here with no CSRF token; the
                        // KHPos starter verifies the bank signature, so CSRF must not block it
                        .ignoringRequestMatchers("/khpos/return")
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(plainHandler))
                // materialise the XSRF-TOKEN cookie so the admin SPA can echo it as X-XSRF-TOKEN
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // the admin SPA bundle under /admin is public (static JS/HTML, no data);
                        // protection is enforced at the API: /api/admin/** requires ADMIN
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                // unauthenticated /api/admin/** calls get 401, not a redirect to /fiokom
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**")))
                .formLogin(form -> form
                        .loginPage("/fiokom")
                        .loginProcessingUrl("/fiokom/belepes")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(cartMergeSuccessHandler)
                        .failureUrl("/fiokom?hiba"))
                .logout(logout -> logout
                        .logoutUrl("/fiokom/kilepes")
                        .logoutSuccessUrl("/"))
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /** On login, merge the guest cart into the customer's cart and re-point the cookie. */
    @Bean
    AuthenticationSuccessHandler cartMergeSuccessHandler(CartService cartService,
                                                         CustomerRepository customers) {
        SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler("/fiokom");
        return (request, response, authentication) -> {
            customers.findByEmailIgnoreCase(authentication.getName()).ifPresent(customer -> {
                String guestToken = readCartCookie(request);
                String surviving = cartService.mergeOnLogin(guestToken, customer.getId());
                if (surviving != null && !surviving.equals(guestToken)) {
                    Cookie cookie = new Cookie(CartController.CART_COOKIE, surviving);
                    cookie.setHttpOnly(true);
                    cookie.setPath("/");
                    cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
                    response.addCookie(cookie);
                }
            });
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private static String readCartCookie(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (CartController.CART_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
