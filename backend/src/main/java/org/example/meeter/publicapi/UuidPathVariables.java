package org.example.meeter.publicapi;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import static org.example.meeter.publicapi.PublicIdentifiers.Kind;

@Component
public class UuidPathVariables implements WebMvcConfigurer {
    private final PublicIdentifiers identifiers;
    public UuidPathVariables(PublicIdentifiers identifiers) { this.identifiers = identifiers; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!Boolean.TRUE.equals(request.getAttribute(UuidApiFilter.ATTRIBUTE))) return true;
                @SuppressWarnings("unchecked")
                Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (variables == null || variables.isEmpty()) return true;
                Map<String, String> translated = new HashMap<>(variables);
                for (var item : variables.entrySet()) {
                    Kind kind = item.getKey().equals("humanId") ? Kind.HUMAN
                        : item.getKey().equals("superpowerId") ? Kind.SUPERPOWER
                        : item.getKey().equals("id") ? pathKind(request.getRequestURI().substring(request.getContextPath().length())) : null;
                    if (kind != null) {
                        try { translated.put(item.getKey(), identifiers.internal(kind, item.getValue()).toString()); }
                        catch (org.springframework.web.server.ResponseStatusException failure) { throw PublicIdentifiers.missing(); }
                    }
                }
                request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, translated);
                return true;
            }
        });
    }
    private static Kind pathKind(String path) {
        if (path.startsWith("/api/people/")) return Kind.HUMAN;
        if (path.startsWith("/api/cliques/")) return Kind.CLIQUE;
        if (path.startsWith("/api/quotes/")) return Kind.QUOTE;
        if (path.startsWith("/api/superpowers/")) return Kind.SUPERPOWER;
        if (path.startsWith("/api/physical-activities/")) return Kind.ACTIVITY;
        if (path.startsWith("/api/quote-games/")) return Kind.GAME;
        if (path.matches("/api/(interactions|meetings|visits|events|trips|flatmates|weddings)/.*")) return Kind.INTERACTION;
        return null;
    }
}
