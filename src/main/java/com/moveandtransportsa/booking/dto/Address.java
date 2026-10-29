package com.moveandtransportsa.booking.dto;

import com.moveandtransportsa.booking.domain.booking.BookingAddress;

/**
 * Value object representing a complete address for distance calculation.
 * Used with Google Maps Distance Matrix API integration.
 */
public record Address(
        String addressLine,
        String suburb,
        String city
) {
    /**
     * Formats address for Google Maps API.
     * Joins address components with commas for API consumption.
     *
     * @return Formatted address string (e.g., "123 Main St, Johannesburg, Gauteng")
     */
    public String toFormattedString() {
        return String.join(", ", addressLine, suburb, city);
    }

    /**
     * Extracts suburb for fallback zone lookup.
     * Used when Google Maps API fails and system falls back to zone-based distance calculation.
     *
     * @return The suburb component of the address
     */
    public String getSuburb() {
        return suburb;
    }

    /**
     * Factory method to convert from BookingAddress entity to Address value object.
     *
     * @param bookingAddress The booking address entity to convert
     * @return Address value object
     */
    public static Address from(BookingAddress bookingAddress) {
        return new Address(
                bookingAddress.getAddressLine(),
                bookingAddress.getSuburb(),
                bookingAddress.getCity()
        );
    }
}
