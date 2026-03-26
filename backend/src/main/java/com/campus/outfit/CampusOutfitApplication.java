package com.campus.outfit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campus.outfit.mapper")
public class CampusOutfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusOutfitApplication.class, args);
    }
}
