package com.motiengineering.bidmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BidmgmtApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidmgmtApplication.class, args);
    }
}
