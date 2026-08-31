package com.apollosuny.apolledgebe;

import com.apollosuny.apolledgebe.auth.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ApolledgeBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApolledgeBeApplication.class, args);
    }

}
