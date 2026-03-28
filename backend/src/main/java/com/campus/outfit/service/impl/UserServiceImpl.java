package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.dto.UserAuthRequest;
import com.campus.outfit.dto.UserUpdateDTO;
import com.campus.outfit.entity.Follow;
import com.campus.outfit.entity.User;
import com.campus.outfit.mapper.FollowMapper;
import com.campus.outfit.mapper.UserMapper;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.UserService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private FollowMapper followMapper;

    @Override
    public Result<String> register(UserAuthRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Result.fail("用户名或密码不能为空");
        }

        // 检查用户名
        if (count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            return Result.fail("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(User.Role.NORMAL);
        user.setFollowCount(0);
        user.setFanCount(0);

        save(user);
        return Result.success("注册成功");
    }

    @Override
    public Result<Map<String, Object>> login(UserAuthRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Result.fail("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user);

        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("userId", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole() != null ? user.getRole().name() : User.Role.NORMAL.name());
        map.put("avatar", user.getAvatar());

        return Result.success(map);
    }

    @Override
    public Result<String> updateProfile(Long userId, UserUpdateDTO dto) {
        User user = new User();
        user.setId(userId);
        
        // 手动映射允许修改的字段，防止 role, fanCount 等敏感字段被注入
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getBio() != null) user.setBio(dto.getBio());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        
        updateById(user);
        return Result.success("修改成功");
    }

    @Override
    @Transactional
    public Result<String> follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            return Result.fail("不能关注自己");
        }
        
        // 检查是否已关注
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (count > 0) {
            return Result.fail("已经关注过了");
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        followMapper.insert(follow);

        // 更新关注数和粉丝数
        update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, followerId)
                .setSql("follow_count = follow_count + 1"));
        update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, followeeId)
                .setSql("fan_count = fan_count + 1"));

        return Result.success("关注成功");
    }

    @Override
    @Transactional
    public Result<String> unfollow(Long followerId, Long followeeId) {
        int deleted = followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        
        if (deleted > 0) {
            update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                    .eq(User::getId, followerId)
                    .setSql("follow_count = follow_count - 1"));
            update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                    .eq(User::getId, followeeId)
                    .setSql("fan_count = fan_count - 1"));
        }
        
        return Result.success("已取消关注");
    }

    @Override
    public IPage<User> getFollowList(Long userId, int page, int size) {
        Page<User> userPage = new Page<>(page, size);
        return followMapper.selectFollowUserPage(userPage, userId);
    }

    @Override
    public IPage<User> getFanList(Long userId, int page, int size) {
        Page<User> userPage = new Page<>(page, size);
        return followMapper.selectFanUserPage(userPage, userId);
    }
}
