package org.example.meeter.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService service;
    public TokenAuthenticationFilter(AuthService service) { this.service = service; }
    static String cookie(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            if (cookie.getName().equals("meeterAuth")) return cookie.getValue();
        return null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        service.authenticate(cookie(request)).ifPresent(user -> {
            var authorities = user.roles().stream().map(role -> new SimpleGrantedAuthority(role.name())).toList();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        });
        chain.doFilter(request, response);
    }
}
