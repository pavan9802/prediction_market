package com.prediction.market.prediction_market.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
@AllArgsConstructor
@Builder
public class TradeRequest {

    String userId;
    String marketId;
    Integer size;
    String outcome; // "Yes" or "No"
}
