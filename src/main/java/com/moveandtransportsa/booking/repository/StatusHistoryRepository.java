package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.booking.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
