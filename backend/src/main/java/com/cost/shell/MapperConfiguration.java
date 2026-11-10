package com.cost.shell;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.cost.shell.menu")
public class MapperConfiguration {
}
