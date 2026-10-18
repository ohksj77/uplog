package com.uplog.portfolio.widget;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 위젯 스냅샷의 주기 쓰기({@link WidgetSnapshotWriter})에 스케줄러를 켠다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class WidgetConfiguration {}
