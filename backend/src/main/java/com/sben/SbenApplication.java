package com.sben;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the Smart Blood Emergency Network API.
 *
 * <p>Enables JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}
 * (and later actor columns) are populated automatically on every entity, and
 * scheduling for the nightly hospital document expiry check.
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableScheduling
@ConfigurationPropertiesScan
public class SbenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SbenApplication.class, args);
    }
}
