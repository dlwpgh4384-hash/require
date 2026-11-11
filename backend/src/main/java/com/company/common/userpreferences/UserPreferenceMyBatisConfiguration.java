package com.company.common.userpreferences;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = UserPreferenceMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class UserPreferenceMyBatisConfiguration {
}
