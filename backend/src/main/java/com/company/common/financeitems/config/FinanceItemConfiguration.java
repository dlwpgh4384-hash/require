package com.company.common.financeitems.config;

import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;

@Configuration(proxyBeanMethods=false)
@MapperScan(basePackages="com.company.common.financeitems.mapper",annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class FinanceItemConfiguration {}
