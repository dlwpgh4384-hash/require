package com.company.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {"com.company.common.mapper", "com.company.common.roles", "navigation.mapper", "navigation.schema"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {
}
