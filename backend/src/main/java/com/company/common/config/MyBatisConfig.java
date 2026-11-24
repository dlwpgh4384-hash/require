package com.company.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {"com.company.common.manpowerboard.mapper", "com.company.common.salespipeline.mapper", "com.company.common.salesopportunities.mapper", "com.company.common.candidates.mapper", "com.company.common.projectcostanalyses.mapper", "com.company.common.personalcostprofits.mapper", "com.company.common.bills.mapper", "com.company.common.receipts.mapper", "com.company.common.commonops.mapper", "com.company.common.common", "com.company.common.billchecks.mapper", "com.company.common.projectissues.mapper", "com.company.common.manpowercomparisons.mapper", "com.company.common.withdrawals.mapper", "com.company.common.assignments.mapper", "com.company.common.calculation.mapper", "com.company.common.clients.mapper", "com.company.common.codegroups.mapper", "com.company.common.codehistory.mapper", "com.company.common.codemaps.mapper", "com.company.common.menus.mapper", "com.company.common.operatingstandards.mapper", "com.company.common.parts.mapper", "com.company.common.preferences.mapper", "com.company.common.roles.mapper", "com.company.common.uploads.mapper", "com.company.common.users.mapper", "navigation.mapper", "navigation.schema"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {
}
