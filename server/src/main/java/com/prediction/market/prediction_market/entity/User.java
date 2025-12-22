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
public class User {
    String userId;
    double balance;

    public boolean hasSufficientBalance(double amount) {
        return balance >= amount;
    }

    public void debit(double amount) {
        balance -= amount;
    }
}
