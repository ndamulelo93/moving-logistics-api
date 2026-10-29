package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.domain.distance.DistanceCacheEntity;
import com.moveandtransportsa.booking.repository.DistanceCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for caching distance calculations to minimize API costs and improve performance.
 * Provides persistent caching with TTL management and bidirectional lookup support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistanceCache {

    private final DistanceCacheRepository repository;
    private final GoogleMapsConfig config;

    /**
     * Normalizes address for consistent cache key generation.
     * Converts to lowercase and removes extra whitespace.
     *
     * @param address Raw address string
     * @return Normalized address
     */
    public String normalize(String address) {
        if (address == null) {
            return "";
        }
        return address.trim().toLowerCase();
    }

    /**
     * Looks up cached distance for address pair.
     * Supports bidirectional lookup: cache(A,B) == cache(B,A)
     * Updates accessed_at timestamp on hit.
     * Validates TTL using cacheTtlDays configuration.
     *
     * @param pickup Pickup address (will be normalized)
     * @param dropoff Dropoff address (will be normalized)
     * @return Optional containing distance if found and fresh, empty otherwise
     */
    @Transactional
    public Optional<BigDecimal> lookup(String pickup, String dropoff) {
        try {
            String normalizedPickup = normalize(pickup);
            String normalizedDropoff = normalize(dropoff);

            Optional<DistanceCacheEntity> cacheEntry = repository.findByAddressPair(
                normalizedPickup,
                normalizedDropoff
            );

            if (cacheEntry.isEmpty()) {
                log.debug("Distance cache miss: pickup={}, dropoff={}", normalizedPickup, normalizedDropoff);
                return Optional.empty();
            }

            DistanceCacheEntity entity = cacheEntry.get();

            // Validate TTL
            Instant cutoffDate = Instant.now().minus(config.getCacheTtlDays(), ChronoUnit.DAYS);
            if (entity.getCreatedAt().isBefore(cutoffDate)) {
                log.debug("Distance cache entry stale: pickup={}, dropoff={}, age={}days",
                    normalizedPickup, normalizedDropoff,
                    ChronoUnit.DAYS.between(entity.getCreatedAt(), Instant.now()));
                return Optional.empty();
            }

            // Update accessed_at timestamp on cache hit
            entity.setAccessedAt(Instant.now());
            repository.save(entity);

            log.debug("Distance cache hit: pickup={}, dropoff={}, distance={}km",
                normalizedPickup, normalizedDropoff, entity.getDistanceKm());

            return Optional.of(entity.getDistanceKm());

        } catch (Exception e) {
            log.warn("Distance cache lookup failed, proceeding with API call: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores distance calculation result in cache.
     * Normalizes addresses and sorts alphabetically for bidirectional support.
     * Saves with current timestamps for both created_at and accessed_at.
     *
     * @param pickup Pickup address (will be normalized)
     * @param dropoff Dropoff address (will be normalized)
     * @param distanceKm Distance value to cache
     */
    @Transactional
    public void store(String pickup, String dropoff, BigDecimal distanceKm) {
        try {
            String normalizedPickup = normalize(pickup);
            String normalizedDropoff = normalize(dropoff);

            // Sort alphabetically for bidirectional support
            String addr1 = normalizedPickup;
            String addr2 = normalizedDropoff;
            if (normalizedPickup.compareTo(normalizedDropoff) > 0) {
                addr1 = normalizedDropoff;
                addr2 = normalizedPickup;
            }

            Instant now = Instant.now();

            // Check if entry already exists (bidirectional)
            Optional<DistanceCacheEntity> existing = repository.findByAddressPair(addr1, addr2);

            if (existing.isPresent()) {
                // Update existing entry
                DistanceCacheEntity entity = existing.get();
                entity.setDistanceKm(distanceKm);
                entity.setAccessedAt(now);
                repository.save(entity);
                log.debug("Distance cache updated: pickup={}, dropoff={}, distance={}km",
                    addr1, addr2, distanceKm);
            } else {
                // Create new entry
                DistanceCacheEntity entity = DistanceCacheEntity.builder()
                    .pickupAddress(addr1)
                    .dropoffAddress(addr2)
                    .distanceKm(distanceKm)
                    .createdAt(now)
                    .accessedAt(now)
                    .build();

                repository.save(entity);
                log.debug("Distance cache stored: pickup={}, dropoff={}, distance={}km",
                    addr1, addr2, distanceKm);
            }

        } catch (Exception e) {
            log.warn("Distance cache store failed, continuing without caching: {}", e.getMessage());
        }
    }

    /**
     * Scheduled cleanup job that runs daily at 2 AM to delete stale cache entries.
     * Removes entries where accessed_at is older than 30 days.
     * This prevents the cache table from growing indefinitely with unused entries.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupStaleEntries() {
        try {
            Instant cutoffDate = Instant.now().minus(30, ChronoUnit.DAYS);
            int deletedCount = repository.deleteStaleEntries(cutoffDate);
            
            if (deletedCount > 0) {
                log.info("Distance cache cleanup completed: deleted {} stale entries older than 30 days", deletedCount);
            } else {
                log.debug("Distance cache cleanup completed: no stale entries found");
            }
        } catch (Exception e) {
            log.error("Distance cache cleanup failed: {}", e.getMessage(), e);
        }
    }
}
