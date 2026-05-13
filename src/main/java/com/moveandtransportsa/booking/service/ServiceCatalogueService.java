package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.dto.response.ServiceOfferingResponse;
import com.moveandtransportsa.booking.dto.response.VehicleTypeResponse;
import com.moveandtransportsa.booking.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceCatalogueService {

    private final ServiceOfferingRepository serviceOfferingRepository;

    /**
     * Returns all active service offerings with their associated vehicle types.
     */
    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> listActiveServices() {
        return serviceOfferingRepository.findAllByActiveTrue()
                .stream()
                .map(service -> new ServiceOfferingResponse(
                        service.getId(),
                        service.getName(),
                        service.getDescription(),
                        service.getVehicleTypes() == null ? List.of() :
                                service.getVehicleTypes().stream()
                                        .map(vt -> new VehicleTypeResponse(
                                                vt.getId(),
                                                vt.getName(),
                                                vt.getDescription(),
                                                vt.getLoadCapacity(),
                                                vt.getBaseFee(),
                                                vt.isActive()
                                        ))
                                        .toList()
                ))
                .toList();
    }
}
