package com.moveandtransportsa.booking.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveandtransportsa.booking.dto.request.QuoteRequestWithAddresses;
import com.moveandtransportsa.booking.dto.response.QuoteResponse;
import com.moveandtransportsa.booking.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for QuoteController endpoints.
 * Validates the REST API layer including request validation,
 * endpoint mapping, and response serialization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuoteService quoteService;

    @Test
    void calculateWithAddresses_shouldReturnQuoteResponse_whenValidRequest() throws Exception {
        // Arrange
        QuoteRequestWithAddresses request = new QuoteRequestWithAddresses(
                "123 Main Street",
                "Johannesburg",
                "Gauteng",
                "456 Oak Avenue",
                "Pretoria",
                "Gauteng",
                1L,
                2,
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                false
        );

        QuoteResponse mockResponse = new QuoteResponse(
                new BigDecimal("1500.00"),
                new BigDecimal("45.50"),
                null
        );

        when(quoteService.calculateWithAddresses(any(QuoteRequestWithAddresses.class)))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/quotes/calculate-with-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estimatedTotal").value("1500.00"))
                .andExpect(jsonPath("$.distanceKm").value("45.50"));

        verify(quoteService).calculateWithAddresses(any(QuoteRequestWithAddresses.class));
    }

    @Test
    void calculateWithAddresses_shouldReturn400_whenPickupAddressLineBlank() throws Exception {
        // Arrange
        QuoteRequestWithAddresses request = new QuoteRequestWithAddresses(
                "", // blank pickup address line
                "Johannesburg",
                "Gauteng",
                "456 Oak Avenue",
                "Pretoria",
                "Gauteng",
                1L,
                2,
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                false
        );

        // Act & Assert
        mockMvc.perform(post("/api/quotes/calculate-with-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculateWithAddresses_shouldReturn400_whenVehicleTypeIdNull() throws Exception {
        // Arrange
        QuoteRequestWithAddresses request = new QuoteRequestWithAddresses(
                "123 Main Street",
                "Johannesburg",
                "Gauteng",
                "456 Oak Avenue",
                "Pretoria",
                "Gauteng",
                null, // null vehicle type ID
                2,
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                false
        );

        // Act & Assert
        mockMvc.perform(post("/api/quotes/calculate-with-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculateWithAddresses_shouldReturn400_whenHelpersNegative() throws Exception {
        // Arrange
        QuoteRequestWithAddresses request = new QuoteRequestWithAddresses(
                "123 Main Street",
                "Johannesburg",
                "Gauteng",
                "456 Oak Avenue",
                "Pretoria",
                "Gauteng",
                1L,
                -1, // negative helpers
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                false
        );

        // Act & Assert
        mockMvc.perform(post("/api/quotes/calculate-with-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculateWithAddresses_shouldAcceptRequest_whenServiceTimeIsNull() throws Exception {
        // Arrange
        QuoteRequestWithAddresses request = new QuoteRequestWithAddresses(
                "123 Main Street",
                "Johannesburg",
                "Gauteng",
                "456 Oak Avenue",
                "Pretoria",
                "Gauteng",
                1L,
                2,
                LocalDate.now().plusDays(3),
                null, // service time is optional
                false
        );

        QuoteResponse mockResponse = new QuoteResponse(
                new BigDecimal("1500.00"),
                new BigDecimal("45.50"),
                null
        );

        when(quoteService.calculateWithAddresses(any(QuoteRequestWithAddresses.class)))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/quotes/calculate-with-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedTotal").value("1500.00"));
    }
}
