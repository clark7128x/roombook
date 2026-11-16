package roombook.accounts.security;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import roombook.accounts.application.CredentialsQueries;
import roombook.platform.*;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration implements WebMvcConfigurer {
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http, org.springframework.core.env.Environment environment) throws Exception {
        http.securityMatcher(request -> {
            Integer port = environment.getProperty("local.management.port", Integer.class);
            return port != null && port != 0 && request.getLocalPort() == port;
        }).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
    @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver());
    }

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http, AccountAuthenticationProvider provider,
            CredentialsQueries queries, SecurityErrorHandler errors, ErrorLogging logging, ClientClassifier clients,
            @Value("${roombook.security.authenticated-session-timeout:8h}") Duration authenticatedTimeout) throws Exception {
        var cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && clients.classify(request) == ClientClassifier.Client.PAGE
                && !request.getServletPath().startsWith("/api/")
                && !request.getServletPath().startsWith("/fragments/")
                && request.getHeader("HX-Request") == null);
        var success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setRequestCache(cache);
        var log = LoggerFactory.getLogger(SecurityConfiguration.class);
        http.authenticationProvider(provider)
                .csrf(config -> config.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'"))
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                .requestCache(config -> config.requestCache(cache))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/join", "/logout", "/api/v1/csrf", "/css/**", "/js/**", "/fonts/**", "/icons.svg", "/favicon.ico").permitAll()
                        .requestMatchers("/admin/**", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms", "/api/v1/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rooms", "/api/v1/rooms/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .formLogin(form -> form.loginPage("/login").successHandler((request, response, authentication) -> {
                    request.getSession().setMaxInactiveInterval(Math.toIntExact(authenticatedTimeout.toSeconds()));
                    var principal = (ActorPrincipal) authentication.getPrincipal();
                    log.atInfo().addKeyValue("event", "LOGIN_SUCCESS").addKeyValue("userId", principal.userId()).log("Login succeeded");
                    success.onAuthenticationSuccess(request, response, authentication);
                }).failureHandler((request, response, exception) -> {
                    String username = request.getParameter("username");
                    username = username == null ? "" : username.substring(0, Math.min(username.length(), 64)).replaceAll("[\\p{Cntrl}]", "_");
                    log.atWarn().addKeyValue("event", "LOGIN_FAILURE").addKeyValue("username", username).log("Login failed");
                    response.sendRedirect(request.getContextPath() + "/login?error");
                }))
                .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
                    Long userId = authentication != null && authentication.getPrincipal() instanceof ActorPrincipal principal ? principal.userId() : null;
                    log.atInfo().addKeyValue("event", "LOGOUT").addKeyValue("userId", userId).log("Logout completed");
                    if (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")) {
                        response.setStatus(204);
                    } else response.sendRedirect(request.getContextPath() + "/login?logout");
                }))
                // Header writer wraps failures from identity refresh as well as downstream MVC responses.
                .addFilterAfter(new CurrentUserFilter(queries, errors, logging), HeaderWriterFilter.class);
        return http.build();
    }
}
