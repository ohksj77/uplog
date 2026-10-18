package com.uplog.portfolio.connection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConnectionProperties.class)
public class ConnectionConfiguration {}
