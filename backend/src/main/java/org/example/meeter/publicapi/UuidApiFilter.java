package org.example.meeter.publicapi;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Route the UUID wire contract through the same authenticated application handlers. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class UuidApiFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = UuidApiFilter.class.getName();
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String prefix = request.getContextPath() + "/api/v2";
        if (!request.getRequestURI().startsWith(prefix + "/")) {
            chain.doFilter(request, response); return;
        }
        request.setAttribute(ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getRequestURI() {
                return request.getContextPath() + "/api" + request.getRequestURI().substring(prefix.length());
            }
            @Override public String getServletPath() {
                return "/api" + request.getRequestURI().substring(prefix.length());
            }
            @Override public StringBuffer getRequestURL() {
                String original = request.getRequestURL().toString();
                return new StringBuffer(original.substring(0, original.length() - request.getRequestURI().length()) + getRequestURI());
            }
        }, response);
    }
}
