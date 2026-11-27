package com.company.common.dailybatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 새 매퍼 패키지는 @MapperScan을 같이 둔다(명세 0장: 없으면 앱이 안 켜짐). */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.company.common.dailybatch.mapper")
public class DailyBatchConfiguration {}
