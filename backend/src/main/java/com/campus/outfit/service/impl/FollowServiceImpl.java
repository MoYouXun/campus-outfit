package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.Follow;
import com.campus.outfit.entity.User;
import com.campus.outfit.mapper.FollowMapper;
import com.campus.outfit.mapper.UserMapper;
import com.campus.outfit.service.FollowService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public Result<String> follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            return Result.fail("不能关注自己");
        }
        if (isFollowing(followerId, followeeId)) {
            return Result.fail("已关注");
        }
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        save(follow);

        // 更新用户表的关注数和粉丝数
        updateUserCounts(followerId, 1, 0);
        updateUserCounts(followeeId, 0, 1);

        return Result.success("关注成功");
    }

    @Override
    @Transactional
    public Result<String> unfollow(Long followerId, Long followeeId) {
        boolean removed = remove(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (removed) {
            updateUserCounts(followerId, -1, 0);
            updateUserCounts(followeeId, 0, -1);
        }
        return Result.success("取消关注");
    }

    @Override
    public IPage<Follow> getFollowers(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFolloweeId, userId)
                .orderByDesc(Follow::getCreateTime));
    }

    @Override
    public IPage<Follow> getFollowings(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .orderByDesc(Follow::getCreateTime));
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) return false;
        return count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId)) > 0;
    }

    private void updateUserCounts(Long userId, int followDelta, int fanDelta) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (followDelta != 0) {
            wrapper.setSql("follow_count = GREATEST(0, follow_count + " + followDelta + ")");
        }
        if (fanDelta != 0) {
            wrapper.setSql("fan_count = GREATEST(0, fan_count + " + fanDelta + ")");
        }
        userMapper.update(null, wrapper);
    }
}
