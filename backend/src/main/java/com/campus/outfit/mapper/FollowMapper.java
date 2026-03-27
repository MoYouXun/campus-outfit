package com.campus.outfit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Follow;
import com.campus.outfit.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("SELECT u.* FROM user u JOIN follow f ON u.id = f.followee_id WHERE f.follower_id = #{userId}")
    IPage<User> selectFollowUserPage(IPage<User> page, @Param("userId") Long userId);

    @Select("SELECT u.* FROM user u JOIN follow f ON u.id = f.follower_id WHERE f.followee_id = #{userId}")
    IPage<User> selectFanUserPage(IPage<User> page, @Param("userId") Long userId);
}

