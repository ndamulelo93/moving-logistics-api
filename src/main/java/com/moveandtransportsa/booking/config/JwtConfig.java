package com.moveandtransportsa.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds JWT configuration properties from application.yml under the prefix "app.jwt".
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtConfig {

    /**
     * HMAC-SHA256 signing secret. Must be at least 256 bits (32 characters) in production.
     */
    private String secret;

    /**
     * Access token lifetime in seconds. Default: 900 (15 minutes).
     */
    private int expirySeconds;

    /**
     * Refresh token lifetime in days. Default: 7.
     */
    private int refreshExpiryDays;
}
