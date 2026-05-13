package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.booking.AddressType;
import com.moveandtransportsa.booking.domain.booking.BookingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAddressRepository extends JpaRepository<BookingAddress, Long> {

    List<BookingAddress> findByBookingIdAndAddressType(Long bookingId, AddressType addressType);

    List<BookingAddress> findByBookingIdOrderBySortOrderAsc(Long bookingId);
}
