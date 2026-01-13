package com.prediction.market.prediction_market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.prediction.market.prediction_market.security.JwtAuthenticationFilter;
import com.prediction.market.prediction_market.security.JwtUtil;


@Configuration
public class SecurityConfig {

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    @Bean
    public com.prediction.market.prediction_market.security.SecurityConfig securityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        return new com.prediction.market.prediction_market.security.SecurityConfig(jwtAuthenticationFilter);
    }
}
