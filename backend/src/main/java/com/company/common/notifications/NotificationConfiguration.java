package com.company.common.notifications;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.company.common.notifications.mapper")
public class NotificationConfiguration {}
