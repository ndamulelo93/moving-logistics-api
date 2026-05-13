package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.booking.HistoryEntryType;
import com.moveandtransportsa.booking.domain.booking.StatusHistory;
import com.moveandtransportsa.booking.dto.response.StatusHistoryResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;
    private final BookingRepository bookingRepository;

    /**
     * Appends a STATUS_CHANGE entry to the booking's history.
     */
    @Transactional
    public void appendStatusChange(Long bookingId, BookingStatus previousStatus, BookingStatus newStatus,
                                   String actorType, Long actorAdminId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        StatusHistory entry = StatusHistory.builder()
                .booking(booking)
                .entryType(HistoryEntryType.STATUS_CHANGE)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .actorType(actorType)
                .actorAdminId(actorAdminId)
                .createdAt(Instant.now())
                .build();

        statusHistoryRepository.save(entry);
    }

    /**
     * Appends a PRICE_ADJUSTMENT entry to the booking's history.
     */
    @Transactional
    public void appendPriceAdjustment(Long bookingId, BigDecimal oldPrice, BigDecimal newPrice, Long adminUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        StatusHistory entry = StatusHistory.builder()
                .booking(booking)
                .entryType(HistoryEntryType.PRICE_ADJUSTMENT)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .actorType("ADMIN")
                .actorAdminId(adminUserId)
                .createdAt(Instant.now())
                .build();

        statusHistoryRepository.save(entry);
    }

    /**
     * Returns all history entries for the given booking, ordered by createdAt ascending.
     */
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(Long bookingId) {
        return statusHistoryRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private StatusHistoryResponse toResponse(StatusHistory entry) {
        return new StatusHistoryResponse(
                entry.getId(),
                entry.getEntryType() != null ? entry.getEntryType().name() : null,
                entry.getPreviousStatus() != null ? entry.getPreviousStatus().name() : null,
                entry.getNewStatus() != null ? entry.getNewStatus().name() : null,
                entry.getOldPrice(),
                entry.getNewPrice(),
                entry.getActorType(),
                entry.getActorAdminId(),
                entry.getCreatedAt()
        );
    }
}
