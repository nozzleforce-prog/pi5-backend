package com.ticket.backend.service;

import com.ticket.backend.config.JwtUtil;
import com.ticket.backend.dto.request.RegisterUserRequest;
import com.ticket.backend.dto.response.LoginResponse;
import com.ticket.backend.model.User;
import com.ticket.backend.model.UserRole;
import com.ticket.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, JwtUtil jwtUtil, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(RegisterUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        userRepository.save(user);
    }

    public LoginResponse loginUser(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }

        User dbUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(user.getPassword(), dbUser.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        UserRole role = dbUser.getRole();
        return new LoginResponse(
                jwtUtil.generateToken(dbUser.getUsername(), role),
                dbUser.getUsername(),
                role);
    }
}
