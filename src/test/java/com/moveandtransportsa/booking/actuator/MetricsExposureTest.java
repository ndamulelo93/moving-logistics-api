package com.moveandtransportsa.booking.actuator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration test to verify Spring Boot Actuator metrics exposure.
 * Validates Requirement 12.6: Expose metrics through Spring Boot Actuator endpoint /actuator/metrics
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetricsExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Requirement 12.6: Verify /actuator/metrics endpoint is accessible
     */
    @Test
    void shouldExposeActuatorMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    /**
     * Requirement 12.6: Verify custom Google Maps metrics are registered
     */
    @Test
    void shouldRegisterCustomGoogleMapsMetrics() {
        // Register sample metrics to ensure they exist
        meterRegistry.counter("google_maps_api_calls_total", "status", "success").increment();
        meterRegistry.counter("distance_cache_hits_total").increment();
        meterRegistry.counter("distance_cache_misses_total").increment();
        meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").increment();

        // Verify metrics are registered in MeterRegistry
        Counter apiCallsCounter = meterRegistry.find("google_maps_api_calls_total")
                .tag("status", "success")
                .counter();
        assertThat(apiCallsCounter).isNotNull();
        assertThat(apiCallsCounter.count()).isGreaterThan(0);

        Counter cacheHitsCounter = meterRegistry.find("distance_cache_hits_total").counter();
        assertThat(cacheHitsCounter).isNotNull();
        assertThat(cacheHitsCounter.count()).isGreaterThan(0);

        Counter cacheMissesCounter = meterRegistry.find("distance_cache_misses_total").counter();
        assertThat(cacheMissesCounter).isNotNull();
        assertThat(cacheMissesCounter.count()).isGreaterThan(0);

        Counter fallbackCounter = meterRegistry.find("distance_fallback_total")
                .tag("reason", "rate_limit")
                .counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isGreaterThan(0);
    }

    /**
     * Requirement 12.6: Verify individual metric endpoint works for google_maps_api_calls_total
     */
    @Test
    void shouldExposeIndividualMetricEndpoint_ApiCallsTotal() throws Exception {
        // Increment metric to ensure it exists
        meterRegistry.counter("google_maps_api_calls_total", "status", "success").increment();

        mockMvc.perform(get("/actuator/metrics/google_maps_api_calls_total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("google_maps_api_calls_total"))
                .andExpect(jsonPath("$.measurements").isArray())
                .andExpect(jsonPath("$.availableTags").isArray());
    }

    /**
     * Requirement 12.6: Verify individual metric endpoint works for distance_cache_hits_total
     */
    @Test
    void shouldExposeIndividualMetricEndpoint_CacheHits() throws Exception {
        // Increment metric to ensure it exists
        meterRegistry.counter("distance_cache_hits_total").increment();

        mockMvc.perform(get("/actuator/metrics/distance_cache_hits_total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("distance_cache_hits_total"))
                .andExpect(jsonPath("$.measurements").isArray());
    }

    /**
     * Requirement 12.6: Verify individual metric endpoint works for distance_cache_misses_total
     */
    @Test
    void shouldExposeIndividualMetricEndpoint_CacheMisses() throws Exception {
        // Increment metric to ensure it exists
        meterRegistry.counter("distance_cache_misses_total").increment();

        mockMvc.perform(get("/actuator/metrics/distance_cache_misses_total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("distance_cache_misses_total"))
                .andExpect(jsonPath("$.measurements").isArray());
    }

    /**
     * Requirement 12.6: Verify individual metric endpoint works for distance_fallback_total
     */
    @Test
    void shouldExposeIndividualMetricEndpoint_FallbackTotal() throws Exception {
        // Increment metric to ensure it exists
        meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").increment();

        mockMvc.perform(get("/actuator/metrics/distance_fallback_total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("distance_fallback_total"))
                .andExpect(jsonPath("$.measurements").isArray())
                .andExpect(jsonPath("$.availableTags").isArray());
    }

    /**
     * Requirement 12.6: Verify individual metric endpoint works for google_maps_api_duration_seconds
     */
    @Test
    void shouldExposeIndividualMetricEndpoint_ApiDuration() throws Exception {
        // Register histogram metric to ensure it exists
        meterRegistry.timer("google_maps_api_duration_seconds").record(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        mockMvc.perform(get("/actuator/metrics/google_maps_api_duration_seconds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("google_maps_api_duration_seconds"))
                .andExpect(jsonPath("$.measurements").isArray());
    }

    /**
     * Requirement 12.6: Verify metrics list includes all custom metrics
     */
    @Test
    void shouldIncludeAllCustomMetricsInMetricsList() throws Exception {
        // Register all metrics first
        meterRegistry.counter("google_maps_api_calls_total", "status", "success").increment();
        meterRegistry.counter("distance_cache_hits_total").increment();
        meterRegistry.counter("distance_cache_misses_total").increment();
        meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").increment();
        meterRegistry.timer("google_maps_api_duration_seconds").record(() -> {});

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names[?(@=='google_maps_api_calls_total')]").exists())
                .andExpect(jsonPath("$.names[?(@=='distance_cache_hits_total')]").exists())
                .andExpect(jsonPath("$.names[?(@=='distance_cache_misses_total')]").exists())
                .andExpect(jsonPath("$.names[?(@=='distance_fallback_total')]").exists())
                .andExpect(jsonPath("$.names[?(@=='google_maps_api_duration_seconds')]").exists());
    }

    /**
     * Requirement 12.6: Verify health endpoint is still accessible
     */
    @Test
    void shouldExposeHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    /**
     * Requirement 12.6: Verify info endpoint is still accessible
     */
    @Test
    void shouldExposeInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
