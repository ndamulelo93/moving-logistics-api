package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.zone.SuburbZone;
import com.moveandtransportsa.booking.domain.zone.ZoneDistance;
import com.moveandtransportsa.booking.dto.request.CreateSuburbZoneRequest;
import com.moveandtransportsa.booking.dto.request.CreateZoneDistanceRequest;
import com.moveandtransportsa.booking.dto.request.UpdateSuburbZoneRequest;
import com.moveandtransportsa.booking.dto.request.UpdateZoneDistanceRequest;
import com.moveandtransportsa.booking.dto.response.SuburbZoneResponse;
import com.moveandtransportsa.booking.dto.response.ZoneDistanceResponse;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moveandtransportsa.booking.repository.SuburbZoneRepository;
import com.moveandtransportsa.booking.repository.ZoneDistanceRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final SuburbZoneRepository suburbZoneRepository;
    private final ZoneDistanceRepository zoneDistanceRepository;

    // -------------------------------------------------------------------------
    // Suburb zone operations
    // -------------------------------------------------------------------------

    /**
     * Persists a new suburb zone and returns its response DTO.
     */
    @Transactional
    public SuburbZoneResponse addSuburb(CreateSuburbZoneRequest request) {
        Instant now = Instant.now();
        SuburbZone zone = SuburbZone.builder()
                .suburbName(request.suburbName())
                .zoneName(request.zoneName())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        SuburbZone saved = suburbZoneRepository.save(zone);
        return toSuburbResponse(saved);
    }

    /**
     * Partially updates a suburb zone.
     * Only non-null fields in the request are applied.
     * Throws {@link SuburbNotFoundException} if not found.
     */
    @Transactional
    public SuburbZoneResponse updateSuburb(Long id, UpdateSuburbZoneRequest request) {
        SuburbZone zone = suburbZoneRepository.findById(id)
                .orElseThrow(() -> new SuburbNotFoundException("SuburbZone not found with id: " + id));

        if (request.suburbName() != null) {
            zone.setSuburbName(request.suburbName());
        }
        if (request.zoneName() != null) {
            zone.setZoneName(request.zoneName());
        }
        if (request.active() != null) {
            zone.setActive(request.active());
        }
        zone.setUpdatedAt(Instant.now());

        SuburbZone saved = suburbZoneRepository.save(zone);
        return toSuburbResponse(saved);
    }

    /**
     * Soft-deletes a suburb zone by setting active=false.
     * Throws {@link SuburbNotFoundException} if not found.
     */
    @Transactional
    public void deactivateSuburb(Long id) {
        SuburbZone zone = suburbZoneRepository.findById(id)
                .orElseThrow(() -> new SuburbNotFoundException("SuburbZone not found with id: " + id));
        zone.setActive(false);
        zone.setUpdatedAt(Instant.now());
        suburbZoneRepository.save(zone);
    }

    /**
     * Returns all suburb zones.
     */
    @Transactional(readOnly = true)
    public List<SuburbZoneResponse> listSuburbs() {
        return suburbZoneRepository.findAll()
                .stream()
                .map(this::toSuburbResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Zone distance operations
    // -------------------------------------------------------------------------

    /**
     * Persists a new zone distance and returns its response DTO.
     */
    @Transactional
    public ZoneDistanceResponse addZoneDistance(CreateZoneDistanceRequest request) {
        Instant now = Instant.now();
        ZoneDistance distance = ZoneDistance.builder()
                .zoneFrom(request.zoneFrom())
                .zoneTo(request.zoneTo())
                .distanceKm(request.distanceKm())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ZoneDistance saved = zoneDistanceRepository.save(distance);
        return toDistanceResponse(saved);
    }

    /**
     * Partially updates a zone distance.
     * Only non-null fields in the request are applied.
     * Throws {@link SuburbNotFoundException} if not found.
     */
    @Transactional
    public ZoneDistanceResponse updateZoneDistance(Long id, UpdateZoneDistanceRequest request) {
        ZoneDistance distance = zoneDistanceRepository.findById(id)
                .orElseThrow(() -> new SuburbNotFoundException("ZoneDistance not found with id: " + id));

        if (request.zoneFrom() != null) {
            distance.setZoneFrom(request.zoneFrom());
        }
        if (request.zoneTo() != null) {
            distance.setZoneTo(request.zoneTo());
        }
        if (request.distanceKm() != null) {
            distance.setDistanceKm(request.distanceKm());
        }
        if (request.active() != null) {
            distance.setActive(request.active());
        }
        distance.setUpdatedAt(Instant.now());

        ZoneDistance saved = zoneDistanceRepository.save(distance);
        return toDistanceResponse(saved);
    }

    /**
     * Returns all zone distances.
     */
    @Transactional(readOnly = true)
    public List<ZoneDistanceResponse> listZoneDistances() {
        return zoneDistanceRepository.findAll()
                .stream()
                .map(this::toDistanceResponse)
                .toList();
    }

    /**
     * Resolves the distance in km between two suburbs by looking up their zones
     * and then finding the configured zone-pair distance.
     *
     * @throws SuburbNotFoundException if either suburb is not found, or if no
     *                                 distance is configured for the resolved zone pair.
     */
    @Transactional(readOnly = true)
    public BigDecimal getDistance(String pickupSuburb, String dropoffSuburb) {
        SuburbZone pickupZone = suburbZoneRepository.findBySuburbNameIgnoreCase(pickupSuburb)
                .orElseThrow(() -> new SuburbNotFoundException(pickupSuburb));

        SuburbZone dropoffZone = suburbZoneRepository.findBySuburbNameIgnoreCase(dropoffSuburb)
                .orElseThrow(() -> new SuburbNotFoundException(dropoffSuburb));

        String zoneFrom = pickupZone.getZoneName();
        String zoneTo = dropoffZone.getZoneName();

        ZoneDistance zoneDistance = zoneDistanceRepository.findDistance(zoneFrom, zoneTo)
                .orElseThrow(() -> new SuburbNotFoundException(
                        "No distance configured for zone pair: " + zoneFrom + " -> " + zoneTo));

        return zoneDistance.getDistanceKm();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private SuburbZoneResponse toSuburbResponse(SuburbZone zone) {
        return new SuburbZoneResponse(
                zone.getId(),
                zone.getSuburbName(),
                zone.getZoneName(),
                zone.isActive()
        );
    }

    private ZoneDistanceResponse toDistanceResponse(ZoneDistance distance) {
        return new ZoneDistanceResponse(
                distance.getId(),
                distance.getZoneFrom(),
                distance.getZoneTo(),
                distance.getDistanceKm(),
                distance.isActive()
        );
    }
}
