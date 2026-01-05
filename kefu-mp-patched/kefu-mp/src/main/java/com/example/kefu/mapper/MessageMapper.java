package com.example.kefu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.kefu.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {}
