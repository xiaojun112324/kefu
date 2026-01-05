package com.example.kefu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.kefu.entity.AssignmentHistory;
import com.example.kefu.mapper.AssignmentHistoryMapper;
import com.example.kefu.service.AssignmentHistoryService;
import org.springframework.stereotype.Service;

@Service
public class AssignmentHistoryServiceImpl extends ServiceImpl<AssignmentHistoryMapper, AssignmentHistory> implements AssignmentHistoryService {}
