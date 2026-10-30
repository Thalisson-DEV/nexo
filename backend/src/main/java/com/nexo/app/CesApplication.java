package com.nexo.app;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableAspectJAutoProxy
@ConfigurationPropertiesScan
@EnableScheduling
@EnableJpaRepositories
@EnableCaching
public class CesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CesApplication.class, args);
    }


    // desenvolvido por Thalisson, V1.0
    // ᕙ(⇀‸↼‶)ᕗ
}
