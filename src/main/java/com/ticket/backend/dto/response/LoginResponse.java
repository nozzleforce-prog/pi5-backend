package com.ticket.backend.dto.response;

import com.ticket.backend.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String username;
    private UserRole role;

    public LoginResponse(String token, String username, UserRole role) {
        this.token = token;
        this.username = username;
        this.role = role;
    }
}
