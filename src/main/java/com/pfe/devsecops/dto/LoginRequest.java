package com.pfe.devsecops.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
