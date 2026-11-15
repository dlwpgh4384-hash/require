package com.company.common.developers.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@MapperScan(basePackages = "com.company.common.developers.mapper", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class DeveloperConfiguration {}
