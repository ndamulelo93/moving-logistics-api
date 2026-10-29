package com.moveandtransportsa.booking.metrics;

import com.moveandtransportsa.booking.repository.DistanceCacheRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Component responsible for registering custom metrics for the distance cache.
 * Provides visibility into cache size for monitoring and operational purposes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheMetrics {

    private final DistanceCacheRepository distanceCacheRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Registers custom cache metrics on application startup.
     * Creates a gauge metric that dynamically reports the current size of the distance cache.
     */
    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("distance_cache_size", distanceCacheRepository, DistanceCacheRepository::count)
                .description("Total number of cached distance entries")
                .register(meterRegistry);

        log.info("Cache metrics registered successfully");
    }
}
