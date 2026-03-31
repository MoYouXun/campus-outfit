package com.campus.outfit.dto;

import lombok.Data;

/**
 * 用户信息更新 DTO
 * 仅包含允许用户自助修改的字段，防止非法注入 role, fanCount 等敏感字段
 */
@Data
public class UserUpdateDTO {
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender;
    private String oldPassword;
    private String newPassword;
}
