package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {

    List<VehicleType> findAllByActiveTrue();
}
