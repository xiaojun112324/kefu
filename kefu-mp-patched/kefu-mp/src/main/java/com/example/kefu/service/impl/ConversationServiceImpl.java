package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.entity.Conversation;
import com.example.kefu.mapper.ConversationMapper;
import com.example.kefu.service.ConversationService;
import org.springframework.stereotype.Service;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {}
