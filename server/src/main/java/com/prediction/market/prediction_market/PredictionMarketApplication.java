package com.prediction.market.prediction_market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PredictionMarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(PredictionMarketApplication.class, args);
	}

}
