// src/main/java/com/example/kefu/service/impl/ActivationCodeServiceImpl.java
package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.entity.ActivationCode;
import com.example.kefu.mapper.ActivationCodeMapper;
import com.example.kefu.service.ActivationCodeService;
import org.springframework.stereotype.Service;

@Service
public class ActivationCodeServiceImpl
        extends ServiceImpl<ActivationCodeMapper, ActivationCode>
        implements ActivationCodeService {}
