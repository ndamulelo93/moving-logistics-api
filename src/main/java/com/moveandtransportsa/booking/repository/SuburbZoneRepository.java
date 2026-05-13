package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.zone.SuburbZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SuburbZoneRepository extends JpaRepository<SuburbZone, Long> {

    @Query("SELECT s FROM SuburbZone s WHERE LOWER(s.suburbName) = LOWER(:suburbName) AND s.active = true")
    Optional<SuburbZone> findBySuburbNameIgnoreCase(@Param("suburbName") String suburbName);
}
