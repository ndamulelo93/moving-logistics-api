package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingAssignmentRepository extends JpaRepository<BookingAssignment, Long> {

    @Query("SELECT COUNT(a) > 0 FROM BookingAssignment a WHERE a.driver.id = :driverId AND a.booking.serviceDate = :serviceDate")
    boolean existsConflictForDriver(@Param("driverId") Long driverId, @Param("serviceDate") LocalDate serviceDate);

    @Query("SELECT COUNT(a) > 0 FROM BookingAssignment a WHERE a.vehicle.id = :vehicleId AND a.booking.serviceDate = :serviceDate")
    boolean existsConflictForVehicle(@Param("vehicleId") Long vehicleId, @Param("serviceDate") LocalDate serviceDate);

    @Query("SELECT a FROM BookingAssignment a WHERE a.driver.id = :driverId AND a.booking.serviceDate > :today")
    List<BookingAssignment> findFutureAssignmentsByDriver(@Param("driverId") Long driverId, @Param("today") LocalDate today);

    @Query("SELECT a FROM BookingAssignment a WHERE a.vehicle.id = :vehicleId AND a.booking.serviceDate > :today")
    List<BookingAssignment> findFutureAssignmentsByVehicle(@Param("vehicleId") Long vehicleId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(a) > 0 FROM BookingAssignment a WHERE a.booking.id = :bookingId")
    boolean existsByBookingId(@Param("bookingId") Long bookingId);
}
