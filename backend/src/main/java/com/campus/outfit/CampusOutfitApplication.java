package com.campus.outfit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campus.outfit.mapper")
@EnableScheduling
@EnableAsync
public class CampusOutfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusOutfitApplication.class, args);
    }
}
