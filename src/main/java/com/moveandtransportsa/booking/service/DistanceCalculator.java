package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import com.moveandtransportsa.booking.exception.distance.ApiAuthenticationException;
import com.moveandtransportsa.booking.exception.distance.ApiConnectionException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import com.moveandtransportsa.booking.exception.distance.RateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Primary orchestration service that coordinates distance calculation using Google Maps API
 * with intelligent caching and fallback to zone-based system.
 * 
 * Implements rate limiting to control API costs and prevent quota exhaustion.
 */
@Service
@Slf4j
public class DistanceCalculator {

    private final GoogleMapsClient googleMapsClient;
    private final DistanceCache distanceCache;
    private final ZoneService zoneService;
    private final GoogleMapsConfig config;
    private final MeterRegistry meterRegistry;
    
    private final AtomicInteger requestCountThisMinute;
    private final ScheduledExecutorService scheduler;

    public DistanceCalculator(
            GoogleMapsClient googleMapsClient,
            DistanceCache distanceCache,
            ZoneService zoneService,
            GoogleMapsConfig config,
            MeterRegistry meterRegistry) {
        this.googleMapsClient = googleMapsClient;
        this.distanceCache = distanceCache;
        this.zoneService = zoneService;
        this.config = config;
        this.meterRegistry = meterRegistry;
        
        this.requestCountThisMinute = new AtomicInteger(0);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DistanceCalculator-RateLimitReset");
            thread.setDaemon(true);
            return thread;
        });
        
        // Schedule rate limit counter reset at the start of each minute
        startRateLimitReset();
        
        log.info("DistanceCalculator initialized with rate limit: {} requests/minute", 
                config.getMaxRequestsPerMinute());
    }

    /**
     * Schedules a task to reset the request counter at the start of each minute.
     */
    private void startRateLimitReset() {
        // Calculate delay until next minute boundary
        long currentMillis = System.currentTimeMillis();
        long millisInMinute = 60_000;
        long delayUntilNextMinute = millisInMinute - (currentMillis % millisInMinute);
        
        scheduler.scheduleAtFixedRate(
            () -> {
                int previousCount = requestCountThisMinute.getAndSet(0);
                if (previousCount > 0) {
                    log.debug("Rate limit counter reset. Previous minute had {} requests", previousCount);
                }
            },
            delayUntilNextMinute,
            millisInMinute,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Checks if the current request is within the rate limit.
     * 
     * @return true if under limit, false if limit exceeded
     */
    private boolean checkRateLimit() {
        int currentCount = requestCountThisMinute.get();
        int maxRequests = config.getMaxRequestsPerMinute();
        
        if (currentCount >= maxRequests) {
            log.warn("Rate limit exceeded: {} requests in current minute (max: {})", 
                    currentCount, maxRequests);
            return false;
        }
        
        return true;
    }

    /**
     * Increments the API request counter.
     * Called when an API request is about to be made.
     */
    private void incrementRequestCount() {
        requestCountThisMinute.incrementAndGet();
    }

    /**
     * Calculates distance between two addresses using Google Maps API.
     * Falls back to zone-based calculation on API failure.
     * 
     * @param pickup Full pickup address
     * @param dropoff Full dropoff address
     * @return Distance in kilometers with scale 2
     * @throws com.moveandtransportsa.booking.exception.distance.InvalidAddressException if both API and zone lookup fail
     */
    public BigDecimal getDistance(Address pickup, Address dropoff) {
        // Step 1: Check if pickup equals dropoff after normalization
        String normalizedPickup = distanceCache.normalize(pickup.toFormattedString());
        String normalizedDropoff = distanceCache.normalize(dropoff.toFormattedString());
        
        if (normalizedPickup.equals(normalizedDropoff)) {
            log.debug("Same pickup and dropoff address, returning 0.00: {}", normalizedPickup);
            return BigDecimal.ZERO.setScale(2);
        }
        
        // Step 2: Check if Google Maps integration is disabled
        if (!config.isEnabled()) {
            log.debug("Google Maps integration disabled, using zone fallback");
            meterRegistry.counter("distance_fallback_total", "reason", "integration_disabled").increment();
            return zoneService.getDistance(pickup.getSuburb(), dropoff.getSuburb());
        }
        
        // Step 3: Check rate limit
        if (!checkRateLimit()) {
            log.warn("Rate limit exceeded, using zone fallback: pickup={}, dropoff={}", 
                    pickup.getSuburb(), dropoff.getSuburb());
            meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").increment();
            return zoneService.getDistance(pickup.getSuburb(), dropoff.getSuburb());
        }
        
        // Step 4: Check cache (if caching is enabled)
        if (config.isCacheEnabled()) {
            Optional<BigDecimal> cachedDistance = distanceCache.lookup(
                pickup.toFormattedString(),
                dropoff.toFormattedString()
            );
            
            if (cachedDistance.isPresent()) {
                meterRegistry.counter("distance_cache_hits_total").increment();
                log.debug("Distance cache hit: pickup={}, dropoff={}, distance={}km",
                        normalizedPickup, normalizedDropoff, cachedDistance.get());
                return cachedDistance.get();
            }
            
            meterRegistry.counter("distance_cache_misses_total").increment();
            log.debug("Distance cache miss: pickup={}, dropoff={}", normalizedPickup, normalizedDropoff);
        }
        
        // Step 5: Increment request counter before API call
        incrementRequestCount();
        
        // Step 6: Call Google Maps API
        try {
            BigDecimal distance = googleMapsClient.getDistance(
                pickup.toFormattedString(),
                dropoff.toFormattedString()
            );
            
            // Step 7: Store in cache on success (if caching is enabled)
            if (config.isCacheEnabled()) {
                distanceCache.store(
                    pickup.toFormattedString(),
                    dropoff.toFormattedString(),
                    distance
                );
            }
            
            // Step 8: Increment success metric
            meterRegistry.counter("google_maps_api_calls_total", "status", "success").increment();
            
            // Step 9: Log success
            log.info("Google Maps API distance calculation succeeded: pickup={}, dropoff={}, distance={}km",
                    pickup.getSuburb(), dropoff.getSuburb(), distance);
            
            return distance;
            
        } catch (ApiConnectionException | RateLimitExceededException | ApiAuthenticationException e) {
            // Step 10: Handle API failures with fallback
            return fallbackToZone(pickup, dropoff, e);
        } catch (InvalidAddressException e) {
            // Step 11: Handle invalid address with fallback attempt
            log.warn("Address not found in Google Maps, attempting zone fallback: pickup={}, dropoff={}", 
                    pickup.getSuburb(), dropoff.getSuburb());
            
            try {
                // Attempt zone-based fallback
                meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").increment();
                BigDecimal fallbackDistance = zoneService.getDistance(pickup.getSuburb(), dropoff.getSuburb());
                
                log.warn("Falling back to zone-based distance: pickup={}, dropoff={}, reason=invalid_address",
                        pickup.getSuburb(), dropoff.getSuburb());
                
                return fallbackDistance;
                
            } catch (SuburbNotFoundException suburbException) {
                // Both Google Maps and zone lookup failed, propagate original exception
                log.error("Zone fallback failed - suburb not found: pickup={}, dropoff={}", 
                        pickup.getSuburb(), dropoff.getSuburb());
                throw new InvalidAddressException(
                    "Address not found in Google Maps and suburb not found in zone system: " +
                    pickup.getSuburb() + " or " + dropoff.getSuburb()
                );
            }
        }
    }
    
    /**
     * Falls back to zone-based distance calculation when Google Maps API fails.
     * 
     * @param pickup Pickup address
     * @param dropoff Dropoff address
     * @param exception The exception that triggered the fallback
     * @return Distance in kilometers from zone-based calculation
     */
    private BigDecimal fallbackToZone(Address pickup, Address dropoff, Exception exception) {
        // Extract suburbs from addresses
        String pickupSuburb = pickup.getSuburb();
        String dropoffSuburb = dropoff.getSuburb();
        
        // Determine reason for fallback based on exception type
        String reason = exception.getClass().getSimpleName();
        
        // Log warning with suburbs and reason
        log.warn("Falling back to zone-based distance: pickup={}, dropoff={}, reason={}",
                pickupSuburb, dropoffSuburb, reason);
        
        // Increment fallback metric with reason label
        meterRegistry.counter("distance_fallback_total", "reason", reason).increment();
        
        // Increment failure metric
        meterRegistry.counter("google_maps_api_calls_total", "status", "failure").increment();
        
        // Call ZoneService with extracted suburbs
        return zoneService.getDistance(pickupSuburb, dropoffSuburb);
    }

    /**
     * Calculates distance using zone-based system (backward compatibility).
     * 
     * @param pickupSuburb Pickup suburb name
     * @param dropoffSuburb Dropoff suburb name
     * @return Distance in kilometers
     * @throws com.moveandtransportsa.booking.exception.SuburbNotFoundException if suburbs not found
     */
    public BigDecimal getDistance(String pickupSuburb, String dropoffSuburb) {
        log.debug("Using zone-based distance calculation for backward compatibility: pickup={}, dropoff={}", 
                pickupSuburb, dropoffSuburb);
        return zoneService.getDistance(pickupSuburb, dropoffSuburb);
    }

    /**
     * Shuts down the rate limit reset scheduler.
     * Called during application shutdown via @PreDestroy lifecycle hook.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DistanceCalculator rate limit scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
