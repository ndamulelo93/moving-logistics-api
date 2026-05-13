package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.zone.ZoneDistance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ZoneDistanceRepository extends JpaRepository<ZoneDistance, Long> {

    @Query("SELECT z FROM ZoneDistance z WHERE z.zoneFrom = :zoneFrom AND z.zoneTo = :zoneTo AND z.active = true")
    Optional<ZoneDistance> findDistance(@Param("zoneFrom") String zoneFrom, @Param("zoneTo") String zoneTo);
}
