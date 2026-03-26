package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.Follow;
import com.campus.outfit.utils.Result;

public interface FollowService extends IService<Follow> {
    Result<String> follow(Long followerId, Long followeeId);
    Result<String> unfollow(Long followerId, Long followeeId);
    IPage<Follow> getFollowers(Long userId, int page, int size);
    IPage<Follow> getFollowings(Long userId, int page, int size);
    boolean isFollowing(Long followerId, Long followeeId);
}
