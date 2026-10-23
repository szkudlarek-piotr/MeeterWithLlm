package org.example.meeter.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean org.springframework.security.core.userdetails.UserDetailsService userDetailsService(UserRepository users) {
        return username -> {
            User user = users.findByUsername(username).orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Nieprawidłowy login lub hasło."));
            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername()).password(user.getPasswordHash())
                .authorities(user.getRoles().stream().map(Enum::name).toArray(String[]::new)).build();
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth, @Value("${app.auth.secure-cookie:false}") boolean secure) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secure).sameSite("Strict").path("/"));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(rules -> rules
                // Allow the servlet container to render the original error status.
                // A direct HTTP request to /error remains denied by the rules below.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .requestCache(cache -> cache.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> { response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"detail\":\"Zaloguj się, aby kontynuować.\"}"); })
                .accessDeniedHandler((request, response, error) -> { response.setStatus(403); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"detail\":\"Brak uprawnień lub nieprawidłowy token CSRF.\"}"); }))
            // Uwierzytelnienie z trwałego tokenu nie jest nowym logowaniem na każde żądanie.
            // Uruchamiamy je po zarządzaniu sesją, przed decyzją autoryzacyjną.
            .addFilterBefore(new TokenAuthenticationFilter(auth), AuthorizationFilter.class);
        return http.build();
    }
}
