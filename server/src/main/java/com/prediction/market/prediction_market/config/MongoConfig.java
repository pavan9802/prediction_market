package com.prediction.market.prediction_market.config;

import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
public class MongoConfig {

    // @Bean
    // MongoClient mongoClient() throws Exception {
    // // Load client keystore
    // KeyStore ks = KeyStore.getInstance("PKCS12");
    // try (FileInputStream fis = new FileInputStream(keystorePath)) {
    // ks.load(fis, keystorePassword.toCharArray());
    // }
    // KeyManagerFactory kmf =
    // KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    // kmf.init(ks, keystorePassword.toCharArray());

    // // Load default JVM truststore (or specify custom)
    // KeyStore ts = KeyStore.getInstance("JKS");
    // try (FileInputStream fis = new
    // FileInputStream(keystorePath.replace("client.p12", "truststore.jks"))) {
    // ts.load(fis, keystorePassword.toCharArray());
    // }
    // TrustManagerFactory tmf =
    // TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    // tmf.init(ts);

    // // Build SSL context
    // SSLContext sslContext = SSLContext.getInstance("TLS");
    // sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    // // Create MongoClient with X.509 auth
    // MongoClientSettings settings = MongoClientSettings.builder()
    // .applyConnectionString(new ConnectionString(mongoUri))
    // .applyToSslSettings(b -> b.enabled(true).context(sslContext))
    // .build();

    // return MongoClients.create(settings);
    // }
    @Bean
    MongoClient mongoClient(MongoProperties mongoProperties) {
        return MongoClients.create(mongoProperties.getUri());
    }

}
