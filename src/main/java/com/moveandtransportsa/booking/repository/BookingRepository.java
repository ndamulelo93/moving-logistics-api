package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByStatusAndServiceDate(BookingStatus status, LocalDate serviceDate);

    @Query("SELECT b FROM Booking b WHERE b.serviceDate = :date")
    List<Booking> findByServiceDate(@Param("date") LocalDate date);
}
