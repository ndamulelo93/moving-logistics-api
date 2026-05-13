package com.moveandtransportsa.booking.web.publicapi;

import com.moveandtransportsa.booking.dto.response.ServiceOfferingResponse;
import com.moveandtransportsa.booking.service.ServiceCatalogueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceCatalogueController {

    private final ServiceCatalogueService serviceCatalogueService;

    @GetMapping
    public ResponseEntity<List<ServiceOfferingResponse>> listServices() {
        return ResponseEntity.ok(serviceCatalogueService.listActiveServices());
    }
}
