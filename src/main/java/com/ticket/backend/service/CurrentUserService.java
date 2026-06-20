package com.ticket.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return Optional.empty();
        }
        return Optional.of(username);
    }
}
