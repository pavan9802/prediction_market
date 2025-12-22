package com.prediction.market.prediction_market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.prediction.market.prediction_market.engine.MarketEngine;

@SpringBootApplication
public class PredictionMarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(PredictionMarketApplication.class, args);

		MarketEngine marketEngine = new MarketEngine();
		int min = 10;
        int max = 25;

        // Create an instance of Random
		for (int i = 0; i < 100; i++) {
			int randomNumber = (int)(Math.random() * ((max - min) + 1)) + min;
			long start = System.nanoTime();
			marketEngine.executeTrade(i % 2 == 0 ? "YES" : "NO", randomNumber);
			long end = System.nanoTime();
			System.out.println("Trade execution time: " + (end - start) / 1_000_000.0 + " ms");
		}
	}

}
