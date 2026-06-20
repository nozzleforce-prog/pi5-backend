package com.ticket.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String username;
    private String password; // Store hashed password
    private UserRole role;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public UserRole getRole() {
        return role == null ? UserRole.OPERATOR : role;
    }
}
