package com.prediction.market.prediction_market.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

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
@Document(collection = "users")
public class User {
    @MongoId
    private String id;

    private String userId;
    private double balance;

    public boolean hasSufficientBalance(double amount) {
        return balance >= amount;
    }

    public void debit(double amount) {
        balance -= amount;
    }
}
