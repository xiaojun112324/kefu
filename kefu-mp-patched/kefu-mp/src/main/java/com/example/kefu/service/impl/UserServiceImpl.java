package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.common.CodeGenerator;
import com.example.kefu.entity.User;
import com.example.kefu.mapper.UserMapper;
import com.example.kefu.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User createUser(User user) {
        // 必填校验
        if (user.getAgentId() == null || user.getUsername() == null || user.getPasswordHash() == null || user.getRole() == null) {
            throw new IllegalArgumentException("代理ID、用户名、密码、权限必须填写");
        }

        // 直接保存原始密码（⚠️注意：这样数据库里就是明文存储，生产环境不安全）
        user.setPasswordHash(user.getPasswordHash());

        // 随机生成12位code
        user.setCode(CodeGenerator.generateCode(12));

        // 设置时间
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        save(user);
        return user;
    }
}