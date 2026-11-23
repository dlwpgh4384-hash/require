package com.company.common.monthcloses.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.company.common.monthcloses.mapper",
    annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MonthCloseConfiguration {}
