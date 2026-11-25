package com.company.common.aianalysis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.company.common.aianalysis.mapper",
    annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AiAnalysisConfiguration {}
