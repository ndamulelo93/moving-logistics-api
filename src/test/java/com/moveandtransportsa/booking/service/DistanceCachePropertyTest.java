package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.domain.distance.DistanceCacheEntity;
import com.moveandtransportsa.booking.repository.DistanceCacheRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link DistanceCache} using jqwik.
 * 
 * Tests verify universal properties across many randomly generated inputs.
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6**
 */
@Tag("Feature: google-maps-distance-integration, Property 1, 2, 3, 11 & 14")
class DistanceCachePropertyTest {

    /**
     * **Property 1: Address Normalization Consistency**
     * 
     * For any two address strings that differ only in whitespace or case,
     * the cache lookup SHALL return the same result regardless of which variant is used.
     * 
     * This property ensures that addresses like:
     * - "123 Main St, Johannesburg" and "123 main st, johannesburg"
     * - "  123 Main St  " and "123 Main St"
     * - "123 MAIN ST" and "123 main st"
     * 
     * All normalize to the same cache key and retrieve the same cached distance.
     * 
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    void addressNormalizationConsistency_addressesDifferingOnlyInWhitespaceOrCaseReturnSameResult(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String baseAddress,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String otherAddress,
            @ForAll("distanceValues") BigDecimal cachedDistance) {

        // Given: A DistanceCache with mocked repository
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(7);
        DistanceCache cache = new DistanceCache(repository, config);

        // Create variations of the base address with different whitespace and case
        String variant1 = baseAddress.trim().toLowerCase();
        String variant2 = "  " + baseAddress.toUpperCase() + "  ";
        String variant3 = baseAddress.trim().toUpperCase();
        String variant4 = baseAddress.toLowerCase() + "   ";

        // Mock repository to return a cached entity for the normalized address pair
        String normalizedBase = cache.normalize(baseAddress);
        String normalizedOther = cache.normalize(otherAddress);
        
        DistanceCacheEntity mockEntity = DistanceCacheEntity.builder()
                .id(1L)
                .pickupAddress(normalizedBase)
                .dropoffAddress(normalizedOther)
                .distanceKm(cachedDistance)
                .createdAt(Instant.now())
                .accessedAt(Instant.now())
                .build();

        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.of(mockEntity));
        when(repository.save(any(DistanceCacheEntity.class)))
                .thenReturn(mockEntity);

        // When: Looking up distance with different address variations
        Optional<BigDecimal> result1 = cache.lookup(variant1, otherAddress);
        Optional<BigDecimal> result2 = cache.lookup(variant2, otherAddress);
        Optional<BigDecimal> result3 = cache.lookup(variant3, otherAddress);
        Optional<BigDecimal> result4 = cache.lookup(variant4, otherAddress);

        // Then: All variations should return the same distance
        assertThat(result1).isPresent();
        assertThat(result2).isPresent();
        assertThat(result3).isPresent();
        assertThat(result4).isPresent();
        
        assertThat(result1.get()).isEqualByComparingTo(cachedDistance);
        assertThat(result2.get()).isEqualByComparingTo(cachedDistance);
        assertThat(result3.get()).isEqualByComparingTo(cachedDistance);
        assertThat(result4.get()).isEqualByComparingTo(cachedDistance);
        
        // All lookups should normalize to the same cache key
        String expectedNormalized1 = cache.normalize(variant1);
        String expectedNormalized2 = cache.normalize(variant2);
        String expectedNormalized3 = cache.normalize(variant3);
        String expectedNormalized4 = cache.normalize(variant4);
        
        assertThat(expectedNormalized1).isEqualTo(normalizedBase);
        assertThat(expectedNormalized2).isEqualTo(normalizedBase);
        assertThat(expectedNormalized3).isEqualTo(normalizedBase);
        assertThat(expectedNormalized4).isEqualTo(normalizedBase);
    }

    /**
     * **Property 2: Bidirectional Cache Lookup**
     * 
     * For any two distinct addresses A and B, the cache lookup for (A, B)
     * SHALL return the same distance as the lookup for (B, A).
     * 
     * This property ensures that the order of addresses doesn't matter:
     * - distance(pickup, dropoff) == distance(dropoff, pickup)
     * - cache.lookup("Address A", "Address B") == cache.lookup("Address B", "Address A")
     * 
     * The cache stores entries with addresses sorted alphabetically,
     * and the repository query supports bidirectional lookup.
     * 
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void bidirectionalCacheLookup_cacheABEqualsCacheBA(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressA,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressB,
            @ForAll("distanceValues") BigDecimal cachedDistance) {

        // Given: Ensure addresses are distinct after normalization
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(7);
        DistanceCache cache = new DistanceCache(repository, config);

        String normalizedA = cache.normalize(addressA);
        String normalizedB = cache.normalize(addressB);

        // Skip if addresses normalize to the same value (same-address case tested elsewhere)
        Assume.that(!normalizedA.equals(normalizedB));

        // Mock repository to return cached entity for bidirectional lookup
        // The repository query handles bidirectional lookup: (A,B) or (B,A)
        DistanceCacheEntity mockEntity = DistanceCacheEntity.builder()
                .id(1L)
                .pickupAddress(normalizedA)
                .dropoffAddress(normalizedB)
                .distanceKm(cachedDistance)
                .createdAt(Instant.now())
                .accessedAt(Instant.now())
                .build();

        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.of(mockEntity));
        when(repository.save(any(DistanceCacheEntity.class)))
                .thenReturn(mockEntity);

        // When: Looking up distance in both directions
        Optional<BigDecimal> resultAB = cache.lookup(addressA, addressB);
        Optional<BigDecimal> resultBA = cache.lookup(addressB, addressA);

        // Then: Both lookups should return the same distance
        assertThat(resultAB).isPresent();
        assertThat(resultBA).isPresent();
        assertThat(resultAB.get()).isEqualByComparingTo(resultBA.get());
        assertThat(resultAB.get()).isEqualByComparingTo(cachedDistance);
        
        // Verify repository was called with normalized addresses
        verify(repository, atLeastOnce()).findByAddressPair(normalizedA, normalizedB);
        verify(repository, atLeastOnce()).findByAddressPair(normalizedB, normalizedA);
    }

    /**
     * **Property 2 Extended: Bidirectional Cache Store**
     * 
     * When storing a distance for (A, B), subsequent lookups for both
     * (A, B) and (B, A) should retrieve the same stored distance.
     * 
     * This tests that the store operation properly normalizes and sorts
     * addresses to support bidirectional retrieval.
     * 
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void bidirectionalCacheStore_storeABRetrievableAsBothABAndBA(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressA,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressB,
            @ForAll("distanceValues") BigDecimal distance) {

        // Given: A DistanceCache with mocked repository
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(7);
        DistanceCache cache = new DistanceCache(repository, config);

        String normalizedA = cache.normalize(addressA);
        String normalizedB = cache.normalize(addressB);

        // Skip if addresses normalize to the same value
        Assume.that(!normalizedA.equals(normalizedB));

        // Mock repository to return empty on first lookup (cache miss)
        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Capture the saved entity when store() is called
        when(repository.save(any(DistanceCacheEntity.class)))
                .thenAnswer(invocation -> {
                    DistanceCacheEntity savedEntity = invocation.getArgument(0);
                    // After storing, subsequent lookups should return this entity
                    when(repository.findByAddressPair(anyString(), anyString()))
                            .thenReturn(Optional.of(savedEntity));
                    return savedEntity;
                });

        // When: Storing distance for (A, B)
        cache.store(addressA, addressB, distance);

        // Then: Lookups for both (A, B) and (B, A) should return the same distance
        Optional<BigDecimal> resultAB = cache.lookup(addressA, addressB);
        Optional<BigDecimal> resultBA = cache.lookup(addressB, addressA);

        assertThat(resultAB).isPresent();
        assertThat(resultBA).isPresent();
        assertThat(resultAB.get()).isEqualByComparingTo(distance);
        assertThat(resultBA.get()).isEqualByComparingTo(distance);
        
        // Verify that addresses were stored in sorted order
        // (implementation sorts addresses alphabetically for consistent bidirectional support)
        verify(repository, times(1)).save(argThat(entity -> {
            String addr1 = entity.getPickupAddress();
            String addr2 = entity.getDropoffAddress();
            // Addresses should be stored in alphabetical order
            return addr1.compareTo(addr2) <= 0;
        }));
    }

    /**
     * **Property 3: Cache Freshness Validation**
     * 
     * For any cache entry with age greater than configured TTL days,
     * the system SHALL treat the entry as stale and perform a new API call.
     * 
     * This property ensures that old cached distances are not returned,
     * maintaining data freshness and accuracy over time.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    void cacheFreshnessValidation_entriesOlderThanTTLTriggerNewAPICall(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressA,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressB,
            @ForAll("distanceValues") BigDecimal cachedDistance,
            @ForAll @org.junit.jupiter.api.Tag("ttl-days") int ttlDays) {

        // Constrain TTL to reasonable values (1-30 days)
        Assume.that(ttlDays >= 1 && ttlDays <= 30);

        // Given: A DistanceCache with specific TTL configuration
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(ttlDays);
        DistanceCache cache = new DistanceCache(repository, config);

        String normalizedA = cache.normalize(addressA);
        String normalizedB = cache.normalize(addressB);

        // Skip if addresses normalize to the same value
        Assume.that(!normalizedA.equals(normalizedB));

        // Create a stale cache entry (older than TTL)
        Instant staleCacheTime = Instant.now().minus(ttlDays + 1, java.time.temporal.ChronoUnit.DAYS);
        DistanceCacheEntity staleEntity = DistanceCacheEntity.builder()
                .id(1L)
                .pickupAddress(normalizedA)
                .dropoffAddress(normalizedB)
                .distanceKm(cachedDistance)
                .createdAt(staleCacheTime)
                .accessedAt(staleCacheTime)
                .build();

        // Create a fresh cache entry (within TTL)
        Instant freshCacheTime = Instant.now().minus(ttlDays - 1, java.time.temporal.ChronoUnit.DAYS);
        DistanceCacheEntity freshEntity = DistanceCacheEntity.builder()
                .id(2L)
                .pickupAddress(normalizedA)
                .dropoffAddress(normalizedB)
                .distanceKm(cachedDistance)
                .createdAt(freshCacheTime)
                .accessedAt(freshCacheTime)
                .build();

        // When: Looking up stale entry
        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.of(staleEntity));
        Optional<BigDecimal> staleResult = cache.lookup(addressA, addressB);

        // Then: Stale entry should return empty (treated as cache miss)
        assertThat(staleResult).isEmpty();

        // When: Looking up fresh entry
        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.of(freshEntity));
        when(repository.save(any(DistanceCacheEntity.class)))
                .thenReturn(freshEntity);
        Optional<BigDecimal> freshResult = cache.lookup(addressA, addressB);

        // Then: Fresh entry should return the cached distance
        assertThat(freshResult).isPresent();
        assertThat(freshResult.get()).isEqualByComparingTo(cachedDistance);
    }

    /**
     * **Property 11: Cache Store After Success**
     * 
     * For any successful API call that returns a distance,
     * the system SHALL store the result in the cache with the current timestamp.
     * 
     * This property verifies that successful distance calculations are persisted
     * to minimize future API calls and reduce costs.
     * 
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    void cacheStoreAfterSuccess_successfulAPICallStoresInCache(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressA,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressB,
            @ForAll("distanceValues") BigDecimal distance) {

        // Given: A DistanceCache with mocked repository
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(7);
        DistanceCache cache = new DistanceCache(repository, config);

        String normalizedA = cache.normalize(addressA);
        String normalizedB = cache.normalize(addressB);

        // Skip if addresses normalize to the same value
        Assume.that(!normalizedA.equals(normalizedB));

        // Mock repository to simulate no existing cache entry
        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Instant beforeStore = Instant.now();

        // When: Storing a distance (simulating successful API call)
        cache.store(addressA, addressB, distance);

        Instant afterStore = Instant.now();

        // Then: Repository save should be called with correct data
        verify(repository, times(1)).save(argThat(entity -> {
            // Verify stored distance matches
            boolean distanceMatches = entity.getDistanceKm().compareTo(distance) == 0;
            
            // Verify timestamp is current (within the test execution window)
            boolean timestampIsCurrent = 
                !entity.getCreatedAt().isBefore(beforeStore) && 
                !entity.getCreatedAt().isAfter(afterStore);
            
            boolean accessedAtSet = 
                !entity.getAccessedAt().isBefore(beforeStore) && 
                !entity.getAccessedAt().isAfter(afterStore);
            
            // Verify addresses are normalized and present
            boolean addressesStored = 
                entity.getPickupAddress() != null && 
                entity.getDropoffAddress() != null &&
                !entity.getPickupAddress().isEmpty() && 
                !entity.getDropoffAddress().isEmpty();
            
            return distanceMatches && timestampIsCurrent && accessedAtSet && addressesStored;
        }));
    }

    /**
     * **Property 14: Distance Consistency**
     * 
     * For any address pair, multiple consecutive distance calculations
     * SHALL return the same value (from cache) without calling the API
     * until TTL expires.
     * 
     * This property ensures that repeated queries for the same route
     * return consistent results and benefit from caching.
     * 
     * **Validates: Requirements 4.3, 4.4**
     */
    @Property(tries = 100)
    void distanceConsistency_multipleConsecutiveCallsReturnSameCachedValue(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressA,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressB,
            @ForAll("distanceValues") BigDecimal cachedDistance,
            @ForAll @org.junit.jupiter.api.Tag("lookup-count") int lookupCount) {

        // Constrain lookup count to reasonable values (2-20 calls)
        Assume.that(lookupCount >= 2 && lookupCount <= 20);

        // Given: A DistanceCache with a fresh cached entry
        DistanceCacheRepository repository = Mockito.mock(DistanceCacheRepository.class);
        GoogleMapsConfig config = Mockito.mock(GoogleMapsConfig.class);
        when(config.getCacheTtlDays()).thenReturn(7);
        DistanceCache cache = new DistanceCache(repository, config);

        String normalizedA = cache.normalize(addressA);
        String normalizedB = cache.normalize(addressB);

        // Skip if addresses normalize to the same value
        Assume.that(!normalizedA.equals(normalizedB));

        // Create a fresh cache entry
        Instant cacheTime = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
        DistanceCacheEntity cachedEntity = DistanceCacheEntity.builder()
                .id(1L)
                .pickupAddress(normalizedA)
                .dropoffAddress(normalizedB)
                .distanceKm(cachedDistance)
                .createdAt(cacheTime)
                .accessedAt(cacheTime)
                .build();

        when(repository.findByAddressPair(anyString(), anyString()))
                .thenReturn(Optional.of(cachedEntity));
        when(repository.save(any(DistanceCacheEntity.class)))
                .thenReturn(cachedEntity);

        // When: Performing multiple consecutive lookups
        BigDecimal firstResult = null;
        for (int i = 0; i < lookupCount; i++) {
            Optional<BigDecimal> result = cache.lookup(addressA, addressB);
            
            // Then: All lookups should return the same cached distance
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(cachedDistance);
            
            if (firstResult == null) {
                firstResult = result.get();
            } else {
                // Verify consistency across all lookups
                assertThat(result.get()).isEqualByComparingTo(firstResult);
            }
        }

        // Verify that repository was queried multiple times (once per lookup)
        verify(repository, times(lookupCount)).findByAddressPair(anyString(), anyString());
        
        // Verify that accessed_at was updated for each lookup (cache hit updates timestamp)
        verify(repository, times(lookupCount)).save(any(DistanceCacheEntity.class));
    }

    /**
     * Arbitrary provider for distance values.
     * Generates realistic distances between 0.00 and 500.00 km with scale 2.
     */
    @Provide
    Arbitrary<BigDecimal> distanceValues() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("500.00"))
                .ofScale(2);
    }
}
