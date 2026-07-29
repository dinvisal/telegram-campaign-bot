package com.example.campaignbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampaignBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignBotApplication.class, args);
    }
}
