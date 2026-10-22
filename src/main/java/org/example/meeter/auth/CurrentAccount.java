package org.example.meeter.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {
    public record Principal(Long id, String username, Long humanId, String fullName, java.util.Set<Role> roles) {}
    public Principal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Principal p))
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Brak sesji.");
        return p;
    }
    public Long id() { return principal().id(); }
    public boolean isAdmin() { return principal().roles().contains(Role.ROLE_ADMIN); }
    static Principal of(User user) {
        return new Principal(user.getId(), user.getUsername(), user.getHuman().getId(), user.getHuman().getFullName(), java.util.Set.copyOf(user.getRoles()));
    }
}
