package com.company.common.profitstructures.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.company.common.profitstructures.mapper")
public class ProfitStructureConfiguration {}
