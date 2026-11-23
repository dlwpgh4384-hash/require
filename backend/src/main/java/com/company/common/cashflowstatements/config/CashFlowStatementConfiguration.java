package com.company.common.cashflowstatements.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.company.common.cashflowstatements.mapper")
public class CashFlowStatementConfiguration {}
