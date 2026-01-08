package com.prediction.market.prediction_market;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import jakarta.annotation.PostConstruct;

@Component
public class MongoStartUpCheck {

    @Autowired
    MongoClient mongoClient;

    @PostConstruct
    public void checkMongoConnection() {
        try {
            MongoDatabase database = mongoClient.getDatabase("prediction-market");
            MongoCollection<Document> collection = database.getCollection("markets");
            Document doc = collection.find().first();
            if (doc != null) {
                System.out.println(doc.toJson());
            } else {
                System.out.println("No documents found in the collection.");
            }

            System.out.println("✅ MongoDB connection successful");
        } catch (Exception e) {
            throw new RuntimeException("❌ MongoDB connection failed", e);
        }
    }
}
