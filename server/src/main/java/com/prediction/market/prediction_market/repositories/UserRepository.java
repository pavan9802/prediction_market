package com.prediction.market.prediction_market.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

}
