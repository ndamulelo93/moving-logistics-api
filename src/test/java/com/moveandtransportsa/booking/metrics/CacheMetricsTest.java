package com.moveandtransportsa.booking.metrics;

import com.moveandtransportsa.booking.repository.DistanceCacheRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CacheMetrics component.
 * Verifies that the distance_cache_size gauge metric is correctly registered.
 */
class CacheMetricsTest {

    private DistanceCacheRepository mockRepository;
    private MeterRegistry meterRegistry;
    private CacheMetrics cacheMetrics;

    @BeforeEach
    void setUp() {
        mockRepository = Mockito.mock(DistanceCacheRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        cacheMetrics = new CacheMetrics(mockRepository, meterRegistry);
    }

    @Test
    void shouldRegisterDistanceCacheSizeGauge() {
        // Given: Repository with some cached entries
        when(mockRepository.count()).thenReturn(42L);

        // When: Metrics are registered
        cacheMetrics.registerMetrics();

        // Then: Gauge should be registered with correct name
        Gauge gauge = meterRegistry.find("distance_cache_size").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);
    }

    @Test
    void shouldDynamicallyUpdateGaugeValue() {
        // Given: Repository starts with some entries
        when(mockRepository.count()).thenReturn(10L);
        cacheMetrics.registerMetrics();

        Gauge gauge = meterRegistry.find("distance_cache_size").gauge();
        assertThat(gauge.value()).isEqualTo(10.0);

        // When: Cache size changes
        when(mockRepository.count()).thenReturn(25L);

        // Then: Gauge should reflect the new value
        assertThat(gauge.value()).isEqualTo(25.0);
    }

    @Test
    void shouldHandleEmptyCache() {
        // Given: Repository with no entries
        when(mockRepository.count()).thenReturn(0L);

        // When: Metrics are registered
        cacheMetrics.registerMetrics();

        // Then: Gauge should report zero
        Gauge gauge = meterRegistry.find("distance_cache_size").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldIncludeMetricDescription() {
        // Given: Metrics component is set up
        when(mockRepository.count()).thenReturn(5L);

        // When: Metrics are registered
        cacheMetrics.registerMetrics();

        // Then: Gauge should have a description
        Gauge gauge = meterRegistry.find("distance_cache_size").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getDescription())
                .isEqualTo("Total number of cached distance entries");
    }
}
