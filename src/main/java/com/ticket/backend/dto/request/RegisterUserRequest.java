package com.ticket.backend.dto.request;

import com.ticket.backend.model.UserRole;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterUserRequest {
    private String username;
    private String password;
    private UserRole role;
}
