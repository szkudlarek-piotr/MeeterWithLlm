package org.example.meeter.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Order(15)
public class RoleBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final boolean localBootstrap;
    public RoleBootstrap(UserRepository users, @Value("${app.dev.bootstrap:false}") boolean localBootstrap) {
        this.users = users; this.localBootstrap = localBootstrap;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        for (User user : users.findAll()) {
            user.grantRole(Role.ROLE_USER);
            if (localBootstrap && user.getUsername().equals("pszkudlarek")) user.grantRole(Role.ROLE_ADMIN);
        }
    }
}
