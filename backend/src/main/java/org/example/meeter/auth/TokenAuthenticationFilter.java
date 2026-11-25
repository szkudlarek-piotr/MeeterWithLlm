package org.example.meeter.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService service;
    private final boolean secure;
    public TokenAuthenticationFilter(AuthService service, boolean secure) { this.service = service; this.secure = secure; }
    static String cookie(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            if (cookie.getName().equals("meeterAuth")) return cookie.getValue();
        return null;
    }
    static String setCookie(String token, long age, boolean secure) {
        return ResponseCookie.from("meeterAuth", token).httpOnly(true).secure(secure).sameSite("Lax").path("/api").maxAge(age).build().toString();
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String raw = cookie(request);
        service.authenticate(raw).ifPresent(authentication -> {
            var user = authentication.principal();
            var authorities = user.roles().stream().map(role -> new SimpleGrantedAuthority(role.name())).toList();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
            if (authentication.renewed()) response.addHeader(HttpHeaders.SET_COOKIE, setCookie(raw, AuthService.LIFETIME_SECONDS, secure));
        });
        chain.doFilter(request, response);
    }
}
