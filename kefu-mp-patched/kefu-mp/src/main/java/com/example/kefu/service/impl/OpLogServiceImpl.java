package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.entity.OpLog;
import com.example.kefu.mapper.OpLogMapper;
import com.example.kefu.service.OpLogService;
import org.springframework.stereotype.Service;

@Service
public class OpLogServiceImpl extends ServiceImpl<OpLogMapper, OpLog> implements OpLogService {}
