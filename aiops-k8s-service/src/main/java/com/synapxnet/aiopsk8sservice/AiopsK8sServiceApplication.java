package com.synapxnet.aiopsk8sservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiopsK8sServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiopsK8sServiceApplication.class, args);
    }
}
