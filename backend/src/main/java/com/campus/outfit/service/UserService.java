package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.dto.UserAuthRequest;
import com.campus.outfit.dto.UserUpdateDTO;
import com.campus.outfit.entity.User;
import com.campus.outfit.utils.Result;

import java.util.Map;

public interface UserService extends IService<User> {

    Result<String> register(UserAuthRequest request);
    Result<Map<String, Object>> login(UserAuthRequest request);
    Result<String> updateProfile(Long userId, UserUpdateDTO dto);

    Result<String> follow(Long followerId, Long followeeId);
    Result<String> unfollow(Long followerId, Long followeeId);
    IPage<User> getFollowList(Long userId, int page, int size);
    IPage<User> getFanList(Long userId, int page, int size);
}
