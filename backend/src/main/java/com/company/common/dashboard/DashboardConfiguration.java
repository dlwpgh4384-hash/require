package com.company.common.dashboard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.company.common.dashboard.mapper")
public class DashboardConfiguration {}
