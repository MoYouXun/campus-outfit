package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.dto.UserAuthRequest;
import com.campus.outfit.entity.User;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.UserService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.campus.outfit.dto.UserUpdateDTO;
import com.campus.outfit.vo.UserDetailVO;
import com.campus.outfit.service.FollowService;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FollowService followService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/register")
    public Result<String> register(@RequestBody UserAuthRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody UserAuthRequest request) {
        return userService.login(request);
    }

    @GetMapping("/{id}")
    public Result<UserDetailVO> getInfo(@PathVariable Long id, @RequestParam(required = false) Long currentUserId) {
        User user = userService.getById(id);
        if (user == null) return Result.fail("用户不存在");
        
        boolean isFollowing = false;
        if (currentUserId != null) {
            isFollowing = followService.isFollowing(currentUserId, id);
        }
        
        return Result.success(new UserDetailVO(user, isFollowing));
    }

    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody UserUpdateDTO userUpdateDTO, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return userService.updateProfile(userId, userUpdateDTO);
    }



    @PostMapping("/follow/{id}")
    public Result<String> follow(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long followerId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return userService.follow(followerId, id);
    }

    @DeleteMapping("/follow/{id}")
    public Result<String> unfollow(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long followerId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return userService.unfollow(followerId, id);
    }

    @GetMapping("/{id}/follows")
    public Result<IPage<User>> getFollows(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.getFollowList(id, page, size));
    }

    @GetMapping("/{id}/fans")
    public Result<IPage<User>> getFans(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.getFanList(id, page, size));
    }
}
