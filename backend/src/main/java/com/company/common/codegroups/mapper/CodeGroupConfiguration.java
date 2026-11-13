package com.company.common.codegroups.mapper;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = CodeGroupMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class CodeGroupConfiguration {}
