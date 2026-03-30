package com.campus.outfit.dto;

import lombok.Data;

@Data
public class UserAuthRequest {
    private String username;
    private String password;
    private String email;
    private Integer gender;
}
