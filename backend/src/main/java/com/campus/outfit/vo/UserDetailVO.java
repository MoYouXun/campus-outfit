package com.campus.outfit.vo;

import com.campus.outfit.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailVO {
    private User user;
    private boolean isFollowing;
}
