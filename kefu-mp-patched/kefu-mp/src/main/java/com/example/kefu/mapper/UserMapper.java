package com.example.kefu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.kefu.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {}
