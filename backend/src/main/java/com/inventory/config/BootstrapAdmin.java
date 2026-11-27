package com.inventory.config;

import com.inventory.entity.User;
import com.inventory.repository.UserRepository;
import com.inventory.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit first-run setup. Existing users and passwords are never overwritten. */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapAdmin implements ApplicationRunner {
    private final UserRepository users;
    private final UserService userService;
    private final String email;
    private final String password;

    public BootstrapAdmin(UserRepository users, UserService userService,
                          @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
                          @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password) {
        this.users = users;
        this.userService = userService;
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap account already exists. Disable APP_BOOTSTRAP_ENABLED and restart.");
        }
        if (users.count() != 0) {
            throw new IllegalStateException("Bootstrap requires an empty user table. Ask an existing Admin to create accounts.");
        }
        userService.registerUser("MOUNTAIN Administrator", email, password, User.Role.ADMIN);
    }
}
