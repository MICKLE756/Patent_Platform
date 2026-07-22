package com.AI_Assistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.AI_Assistant.userCenter.auth.config.WechatConfig;

@SpringBootApplication
@MapperScan({"com.AI_Assistant.userCenter.**.mapper", "com.AI_Assistant.asyncMessage.**.mapper", "com.AI_Assistant.backend.**.mapper"})
@EnableConfigurationProperties(WechatConfig.class)
public class AIApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIApplication.class, args);
    }
}
