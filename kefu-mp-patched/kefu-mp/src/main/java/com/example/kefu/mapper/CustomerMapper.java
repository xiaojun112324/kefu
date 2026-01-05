package com.example.kefu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.kefu.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
