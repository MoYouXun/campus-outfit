package com.campus.outfit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.outfit.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
