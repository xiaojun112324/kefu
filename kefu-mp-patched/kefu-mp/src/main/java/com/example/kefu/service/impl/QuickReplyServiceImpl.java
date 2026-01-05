// src/main/java/com/xx/app/qa/service/impl/QuickReplyServiceImpl.java
package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.entity.QuickReply;
import com.example.kefu.mapper.QuickReplyMapper;
import com.example.kefu.service.QuickReplyService;
import org.springframework.stereotype.Service;

@Service
public class QuickReplyServiceImpl extends ServiceImpl<QuickReplyMapper, QuickReply>
        implements QuickReplyService {}
