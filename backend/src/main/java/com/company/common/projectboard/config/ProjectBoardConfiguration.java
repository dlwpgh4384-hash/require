package com.company.common.projectboard.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.company.common.projectboard.mapper",
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class ProjectBoardConfiguration {}
