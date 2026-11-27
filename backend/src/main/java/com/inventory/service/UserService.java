package com.inventory.service;

import com.inventory.entity.User;
import com.inventory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String fullName, String email, String rawPassword, User.Role role) {
        if (fullName == null || fullName.isBlank() || fullName.length() > 100 ||
                email == null || email.length() > 150 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                rawPassword == null || rawPassword.length() < 8 ||
                rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || role == null) {
            throw new IllegalArgumentException("Provide a name, valid email, role and a password of at least 8 characters and at most 72 UTF-8 bytes");
        }
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);

        return userRepository.save(user);
    }
}
