package com.ticket.backend.controller;

import com.ticket.backend.dto.response.LoginResponse;
import com.ticket.backend.model.User;
import com.ticket.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        userService.registerUser(user);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody User user) {
        LoginResponse loginResponse = userService.loginUser(user);
        return ResponseEntity.ok(loginResponse);
    }
}
