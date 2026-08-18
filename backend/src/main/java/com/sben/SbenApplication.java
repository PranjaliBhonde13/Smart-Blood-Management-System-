package com.sben;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Application entry point for the Smart Blood Emergency Network API.
 *
 * <p>Enables JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}
 * (and later actor columns) are populated automatically on every entity.
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class SbenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SbenApplication.class, args);
    }
}
