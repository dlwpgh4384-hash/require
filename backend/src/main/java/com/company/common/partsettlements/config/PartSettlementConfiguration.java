package com.company.common.partsettlements.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@MapperScan(basePackages="com.company.common.partsettlements.mapper",annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class PartSettlementConfiguration {}
