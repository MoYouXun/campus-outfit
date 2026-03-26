package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Follow;
import com.campus.outfit.service.FollowService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
public class FollowController {

    @Autowired
    private FollowService followService;

    @PostMapping("/{followeeId}")
    public Result<String> follow(@PathVariable Long followeeId, @RequestParam Long followerId) {
        return followService.follow(followerId, followeeId);
    }

    @DeleteMapping("/{followeeId}")
    public Result<String> unfollow(@PathVariable Long followeeId, @RequestParam Long followerId) {
        return followService.unfollow(followerId, followeeId);
    }

    @GetMapping("/{userId}/followers")
    public Result<IPage<Follow>> getFollowers(@PathVariable Long userId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(followService.getFollowers(userId, page, size));
    }

    @GetMapping("/{userId}/followings")
    public Result<IPage<Follow>> getFollowings(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return Result.success(followService.getFollowings(userId, page, size));
    }
}
