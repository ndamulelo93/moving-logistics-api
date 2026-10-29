package com.moveandtransportsa.booking.web.publicapi;

import com.moveandtransportsa.booking.dto.request.QuoteRequest;
import com.moveandtransportsa.booking.dto.request.QuoteRequestWithAddresses;
import com.moveandtransportsa.booking.dto.response.QuoteResponse;
import com.moveandtransportsa.booking.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Quote calculation endpoints")
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    @Operation(
            summary = "Calculate quote using suburbs",
            description = "Calculates a transport quote using suburb-based zone distance calculation. " +
                    "Legacy endpoint maintained for backward compatibility."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Quote calculated successfully",
                    content = @Content(schema = @Schema(implementation = QuoteResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Suburb or vehicle type not found"
            )
    })
    public ResponseEntity<QuoteResponse> getQuote(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.ok(quoteService.calculate(request));
    }

    @PostMapping("/calculate-with-addresses")
    @Operation(
            summary = "Calculate quote using full addresses",
            description = "Calculates a transport quote using full address information. " +
                    "Uses Google Maps Distance Matrix API for accurate road distance calculation, " +
                    "with automatic fallback to zone-based calculation if API is unavailable."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Quote calculated successfully",
                    content = @Content(schema = @Schema(implementation = QuoteResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters or address not found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vehicle type not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error or API configuration issue"
            )
    })
    public ResponseEntity<QuoteResponse> getQuoteWithAddresses(
            @Valid @RequestBody QuoteRequestWithAddresses request) {
        return ResponseEntity.ok(quoteService.calculateWithAddresses(request));
    }
}
