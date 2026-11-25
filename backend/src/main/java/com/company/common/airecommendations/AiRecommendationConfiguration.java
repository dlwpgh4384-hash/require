package com.company.common.airecommendations;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.company.common.airecommendations.mapper",
    annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AiRecommendationConfiguration {}
