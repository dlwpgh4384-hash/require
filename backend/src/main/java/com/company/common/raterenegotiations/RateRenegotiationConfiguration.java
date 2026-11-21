package com.company.common.raterenegotiations;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.company.common.raterenegotiations.mapper", annotationClass = Mapper.class)
public class RateRenegotiationConfiguration {}
