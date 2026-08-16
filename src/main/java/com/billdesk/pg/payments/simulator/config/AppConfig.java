package com.billdesk.pg.payments.simulator.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {

    return builder.build();
  }

  @Bean
  public ScheduledExecutorService simulatorScheduler() {

    return Executors.newSingleThreadScheduledExecutor();
  }
}
