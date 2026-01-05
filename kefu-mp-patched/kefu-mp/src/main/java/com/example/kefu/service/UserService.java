package com.example.kefu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.kefu.entity.User;

public interface UserService extends IService<User> {
    User createUser(User user);
}
