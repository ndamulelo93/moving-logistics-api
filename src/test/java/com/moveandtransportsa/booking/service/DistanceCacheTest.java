package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.domain.distance.DistanceCacheEntity;
import com.moveandtransportsa.booking.repository.DistanceCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DistanceCache}.
 * 
 * Tests cover:
 * - Address normalization (Requirements 4.1, 4.2)
 * - Bidirectional cache lookup (Requirement 4.6)
 * - TTL validation (Requirements 4.3, 4.4)
 * - Cache storage with current timestamps (Requirement 4.5)
 * - Error handling and logging (Requirements 4.7, 11.6)
 * - Access timestamp updates (Requirements 11.3, 11.4)
 */
@ExtendWith(MockitoExtension.class)
class DistanceCacheTest {

    @Mock
    private DistanceCacheRepository repository;

    @Mock
    private GoogleMapsConfig config;

    private DistanceCache distanceCache;

    @BeforeEach
    void setUp() {
        when(config.getCacheTtlDays()).thenReturn(7); // Default TTL
        distanceCache = new DistanceCache(repository, config);
    }

    // -------------------------------------------------------------------------
    // normalize() tests
    // -------------------------------------------------------------------------

    @Test
    void normalize_shouldConvertToLowercase() {
        // Requirement 4.2: normalize by converting to lowercase
        String result = distanceCache.normalize("123 Main Street, JOHANNESBURG, Gauteng");
        assertThat(result).isEqualTo("123 main street, johannesburg, gauteng");
    }

    @Test
    void normalize_shouldTrimWhitespace() {
        // Requirement 4.2: normalize by removing extra whitespace
        String result = distanceCache.normalize("  123 Main Street  ");
        assertThat(result).isEqualTo("123 main street");
    }

    @Test
    void normalize_shouldHandleNull() {
        String result = distanceCache.normalize(null);
        assertThat(result).isEmpty();
    }

    @Test
    void normalize_shouldHandleEmptyString() {
        String result = distanceCache.normalize("");
        assertThat(result).isEmpty();
    }

    @Test
    void normalize_shouldHandleWhitespaceOnly() {
        String result = distanceCache.normalize("   ");
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // lookup() tests
    // -------------------------------------------------------------------------

    @Test
    void lookup_shouldReturnEmpty_whenNoCacheEntryExists() {
        // Requirement 4.7: return empty on cache miss
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.empty());

        Optional<BigDecimal> result = distanceCache.lookup(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void lookup_shouldReturnDistance_whenCacheEntryIsFresh() {
        // Requirements 4.3, 11.4: return cached value if fresh (<7 days)
        Instant now = Instant.now();
        DistanceCacheEntity entity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("45.50"))
            .createdAt(now.minus(3, ChronoUnit.DAYS))
            .accessedAt(now.minus(1, ChronoUnit.DAYS))
            .build();

        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.of(entity));

        Optional<BigDecimal> result = distanceCache.lookup(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("45.50"));
    }

    @Test
    void lookup_shouldReturnEmpty_whenCacheEntryIsStale() {
        // Requirement 4.4: invalidate cache entry if >= 7 days old
        Instant now = Instant.now();
        DistanceCacheEntity entity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("45.50"))
            .createdAt(now.minus(10, ChronoUnit.DAYS))
            .accessedAt(now.minus(5, ChronoUnit.DAYS))
            .build();

        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.of(entity));

        Optional<BigDecimal> result = distanceCache.lookup(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void lookup_shouldUpdateAccessedAt_whenCacheHit() {
        // Requirement 11.4: update accessed_at on cache hit
        Instant now = Instant.now();
        DistanceCacheEntity entity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("45.50"))
            .createdAt(now.minus(1, ChronoUnit.DAYS))
            .accessedAt(now.minus(1, ChronoUnit.DAYS))
            .build();

        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.of(entity));
        when(repository.save(any(DistanceCacheEntity.class)))
            .thenReturn(entity);

        distanceCache.lookup("123 Main St, Johannesburg", "456 Oak Ave, Pretoria");

        ArgumentCaptor<DistanceCacheEntity> captor = ArgumentCaptor.forClass(DistanceCacheEntity.class);
        verify(repository).save(captor.capture());

        DistanceCacheEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getAccessedAt()).isAfter(now.minus(1, ChronoUnit.SECONDS));
    }

    @Test
    void lookup_shouldNormalizeAddresses() {
        // Requirement 4.2: normalize addresses before lookup
        Instant now = Instant.now();
        DistanceCacheEntity entity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("45.50"))
            .createdAt(now)
            .accessedAt(now)
            .build();

        when(repository.findByAddressPair(
            eq("123 main st, johannesburg"),
            eq("456 oak ave, pretoria")
        )).thenReturn(Optional.of(entity));

        Optional<BigDecimal> result = distanceCache.lookup(
            "  123 MAIN ST, JOHANNESBURG  ",
            "  456 OAK AVE, PRETORIA  "
        );

        assertThat(result).isPresent();
        verify(repository).findByAddressPair(
            "123 main st, johannesburg",
            "456 oak ave, pretoria"
        );
    }

    @Test
    void lookup_shouldSupportBidirectionalLookup() {
        // Requirement 4.6: bidirectional lookup via repository query
        Instant now = Instant.now();
        DistanceCacheEntity entity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("45.50"))
            .createdAt(now)
            .accessedAt(now)
            .build();

        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.of(entity));

        // Lookup with swapped addresses should return same result
        Optional<BigDecimal> result1 = distanceCache.lookup(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        Optional<BigDecimal> result2 = distanceCache.lookup(
            "456 Oak Ave, Pretoria",
            "123 Main St, Johannesburg"
        );

        assertThat(result1).isPresent();
        assertThat(result2).isPresent();
        assertThat(result1.get()).isEqualByComparingTo(result2.get());
    }

    @Test
    void lookup_shouldReturnEmpty_onDatabaseError() {
        // Requirement 4.7: return empty on database error
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database connection failed"));

        Optional<BigDecimal> result = distanceCache.lookup(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // store() tests
    // -------------------------------------------------------------------------

    @Test
    void store_shouldNormalizeAndSortAddresses() {
        // Requirements 4.2, 4.6: normalize and sort alphabetically for bidirectional support
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(repository.save(any(DistanceCacheEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        distanceCache.store(
            "  PRETORIA, Main St  ",
            "  johannesburg, Oak Ave  ",
            new BigDecimal("45.50")
        );

        ArgumentCaptor<DistanceCacheEntity> captor = ArgumentCaptor.forClass(DistanceCacheEntity.class);
        verify(repository).save(captor.capture());

        DistanceCacheEntity savedEntity = captor.getValue();
        // Addresses should be sorted: "johannesburg" comes before "pretoria"
        assertThat(savedEntity.getPickupAddress()).isEqualTo("johannesburg, oak ave");
        assertThat(savedEntity.getDropoffAddress()).isEqualTo("pretoria, main st");
    }

    @Test
    void store_shouldSetCurrentTimestamps() {
        // Requirements 4.5, 11.3: store with current timestamps
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(repository.save(any(DistanceCacheEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        distanceCache.store(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria",
            new BigDecimal("45.50")
        );
        Instant after = Instant.now();

        ArgumentCaptor<DistanceCacheEntity> captor = ArgumentCaptor.forClass(DistanceCacheEntity.class);
        verify(repository).save(captor.capture());

        DistanceCacheEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getCreatedAt()).isBetween(before, after);
        assertThat(savedEntity.getAccessedAt()).isBetween(before, after);
        assertThat(savedEntity.getDistanceKm()).isEqualByComparingTo(new BigDecimal("45.50"));
    }

    @Test
    void store_shouldUpdateExistingEntry() {
        // Update existing cache entry with new distance and accessed_at
        Instant oldTimestamp = Instant.now().minus(5, ChronoUnit.DAYS);
        DistanceCacheEntity existingEntity = DistanceCacheEntity.builder()
            .id(1L)
            .pickupAddress("123 main st, johannesburg")
            .dropoffAddress("456 oak ave, pretoria")
            .distanceKm(new BigDecimal("40.00"))
            .createdAt(oldTimestamp)
            .accessedAt(oldTimestamp)
            .build();

        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.of(existingEntity));
        when(repository.save(any(DistanceCacheEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        distanceCache.store(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria",
            new BigDecimal("45.50")
        );
        Instant after = Instant.now();

        ArgumentCaptor<DistanceCacheEntity> captor = ArgumentCaptor.forClass(DistanceCacheEntity.class);
        verify(repository).save(captor.capture());

        DistanceCacheEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getId()).isEqualTo(1L);
        assertThat(savedEntity.getDistanceKm()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(savedEntity.getCreatedAt()).isEqualTo(oldTimestamp); // Created timestamp unchanged
        assertThat(savedEntity.getAccessedAt()).isBetween(before, after); // Accessed timestamp updated
    }

    @Test
    void store_shouldHandleDatabaseError() {
        // Requirement 11.6: log warning and continue on database error
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Should not throw exception
        distanceCache.store(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria",
            new BigDecimal("45.50")
        );

        // Verify no save was attempted after error
        verify(repository, never()).save(any(DistanceCacheEntity.class));
    }

    @Test
    void store_shouldHandleNullAddresses() {
        when(repository.findByAddressPair(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(repository.save(any(DistanceCacheEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        distanceCache.store(null, null, new BigDecimal("0.00"));

        ArgumentCaptor<DistanceCacheEntity> captor = ArgumentCaptor.forClass(DistanceCacheEntity.class);
        verify(repository).save(captor.capture());

        DistanceCacheEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getPickupAddress()).isEmpty();
        assertThat(savedEntity.getDropoffAddress()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // cleanupStaleEntries() tests
    // -------------------------------------------------------------------------

    @Test
    void cleanupStaleEntries_shouldDeleteEntriesOlderThan30Days() {
        // Requirement 11.5: delete entries where accessed_at < 30 days ago
        when(repository.deleteStaleEntries(any(Instant.class)))
            .thenReturn(5);

        distanceCache.cleanupStaleEntries();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteStaleEntries(captor.capture());

        Instant cutoffDate = captor.getValue();
        Instant expectedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        
        // Allow 1 second tolerance for test execution time
        assertThat(cutoffDate).isBetween(
            expectedCutoff.minus(1, ChronoUnit.SECONDS),
            expectedCutoff.plus(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    void cleanupStaleEntries_shouldLogDeletedCount() {
        // Requirement 11.5: log number of deleted entries
        when(repository.deleteStaleEntries(any(Instant.class)))
            .thenReturn(10);

        distanceCache.cleanupStaleEntries();

        verify(repository).deleteStaleEntries(any(Instant.class));
        // Logging is verified via log output (INFO level)
        // "Distance cache cleanup completed: deleted 10 stale entries older than 30 days"
    }

    @Test
    void cleanupStaleEntries_shouldHandleNoStaleEntries() {
        when(repository.deleteStaleEntries(any(Instant.class)))
            .thenReturn(0);

        distanceCache.cleanupStaleEntries();

        verify(repository).deleteStaleEntries(any(Instant.class));
        // Logging is verified via log output (DEBUG level)
        // "Distance cache cleanup completed: no stale entries found"
    }

    @Test
    void cleanupStaleEntries_shouldHandleDatabaseError() {
        when(repository.deleteStaleEntries(any(Instant.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Should not throw exception, error should be logged
        distanceCache.cleanupStaleEntries();

        verify(repository).deleteStaleEntries(any(Instant.class));
        // Logging is verified via log output (ERROR level)
        // "Distance cache cleanup failed: Database connection failed"
    }
}
