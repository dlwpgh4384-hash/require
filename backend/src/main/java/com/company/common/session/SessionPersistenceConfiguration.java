package com.company.common.session;

import com.company.common.session.mapper.SessionMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = SessionMapper.class)
public class SessionPersistenceConfiguration {}
