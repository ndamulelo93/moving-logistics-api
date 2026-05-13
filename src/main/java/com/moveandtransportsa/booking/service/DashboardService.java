package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.dto.response.DashboardSummaryResponse;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Set<BookingStatus> REVENUE_STATUSES =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.ASSIGNED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

    private final BookingRepository bookingRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;

    /**
     * Returns a summary of today's bookings for the admin dashboard.
     * Uses Africa/Johannesburg timezone for "today".
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        List<Booking> todayBookings = bookingRepository.findByServiceDate(today);

        // Group by status name and count
        Map<String, Long> bookingsByStatus = todayBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getStatus().name(), Collectors.counting()));

        // Sum finalPrice (or estimatedPrice if finalPrice is null) for revenue-generating statuses
        BigDecimal confirmedRevenue = todayBookings.stream()
                .filter(b -> REVENUE_STATUSES.contains(b.getStatus()))
                .map(b -> b.getFinalPrice() != null ? b.getFinalPrice() : b.getEstimatedPrice())
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count CONFIRMED bookings for today that have no BookingAssignment
        long unassignedConfirmedCount = todayBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> !bookingAssignmentRepository.existsByBookingId(b.getId()))
                .count();

        return new DashboardSummaryResponse(today, bookingsByStatus, confirmedRevenue, unassignedConfirmedCount);
    }
}
