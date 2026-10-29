package com.moveandtransportsa.booking.dto;

import com.moveandtransportsa.booking.domain.booking.BookingAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Address value object.
 */
class AddressTest {

    @Test
    void toFormattedString_shouldJoinAddressComponentsWithCommas() {
        // Given
        Address address = new Address(
                "123 Main Street",
                "Johannesburg",
                "Gauteng"
        );

        // When
        String formatted = address.toFormattedString();

        // Then
        assertThat(formatted).isEqualTo("123 Main Street, Johannesburg, Gauteng");
    }

    @Test
    void getSuburb_shouldReturnSuburbComponent() {
        // Given
        Address address = new Address(
                "456 Oak Avenue",
                "Sandton",
                "Gauteng"
        );

        // When
        String suburb = address.getSuburb();

        // Then
        assertThat(suburb).isEqualTo("Sandton");
    }

    @Test
    void from_shouldConvertBookingAddressToAddress() {
        // Given
        BookingAddress bookingAddress = BookingAddress.builder()
                .addressLine("789 Pine Road")
                .suburb("Pretoria")
                .city("Gauteng")
                .build();

        // When
        Address address = Address.from(bookingAddress);

        // Then
        assertThat(address.addressLine()).isEqualTo("789 Pine Road");
        assertThat(address.suburb()).isEqualTo("Pretoria");
        assertThat(address.city()).isEqualTo("Gauteng");
    }

    @Test
    void from_shouldHandleNullableFields() {
        // Given
        BookingAddress bookingAddress = BookingAddress.builder()
                .addressLine("100 Test Street")
                .suburb(null)
                .city(null)
                .build();

        // When
        Address address = Address.from(bookingAddress);

        // Then
        assertThat(address.addressLine()).isEqualTo("100 Test Street");
        assertThat(address.suburb()).isNull();
        assertThat(address.city()).isNull();
    }

    @Test
    void recordEquality_shouldWorkCorrectly() {
        // Given
        Address address1 = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address address2 = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address address3 = new Address("456 Oak Ave", "Sandton", "Gauteng");

        // Then
        assertThat(address1).isEqualTo(address2);
        assertThat(address1).isNotEqualTo(address3);
        assertThat(address1.hashCode()).isEqualTo(address2.hashCode());
    }
}
