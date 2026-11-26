package com.company.common.mfa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.company.common.mfa.mapper")
public class MfaConfiguration {}
