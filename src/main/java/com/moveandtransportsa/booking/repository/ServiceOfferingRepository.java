package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.service.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByActiveTrue();
}
