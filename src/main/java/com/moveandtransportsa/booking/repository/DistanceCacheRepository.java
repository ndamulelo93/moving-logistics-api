package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.distance.DistanceCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DistanceCacheRepository extends JpaRepository<DistanceCacheEntity, Long> {

    @Query("""
        SELECT dc FROM DistanceCacheEntity dc 
        WHERE (dc.pickupAddress = :addr1 AND dc.dropoffAddress = :addr2)
           OR (dc.pickupAddress = :addr2 AND dc.dropoffAddress = :addr1)
        """)
    Optional<DistanceCacheEntity> findByAddressPair(
        @Param("addr1") String address1,
        @Param("addr2") String address2
    );

    @Modifying
    @Query("DELETE FROM DistanceCacheEntity dc WHERE dc.accessedAt < :cutoffDate")
    int deleteStaleEntries(@Param("cutoffDate") Instant cutoffDate);
}
