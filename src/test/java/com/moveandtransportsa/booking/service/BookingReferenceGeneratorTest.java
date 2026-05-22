package com.moveandtransportsa.booking.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookingReferenceGenerator}.
 *
 * EntityManager is mocked so no database is required.
 *
 * Requirements covered: 2.2
 */
@ExtendWith(MockitoExtension.class)
class BookingReferenceGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private BookingReferenceGenerator generator;

    /** DDL query stub — executeUpdate() returns 0 (no-op for CREATE SEQUENCE IF NOT EXISTS). */
    private Query ddlQuery;

    /** SELECT nextval query stub — returns a configurable sequence value. */
    private Query nextvalQuery;

    @BeforeEach
    void setUp() {
        ddlQuery = mock(Query.class);
        nextvalQuery = mock(Query.class);

        // Stub the DDL query (CREATE SEQUENCE IF NOT EXISTS ...)
        when(entityManager.createNativeQuery(contains("CREATE SEQUENCE"))).thenReturn(ddlQuery);
        when(ddlQuery.executeUpdate()).thenReturn(0);
    }

    // -----------------------------------------------------------------------
    // Test 1: Format matches MTSA-YYXXX for the current year
    // -----------------------------------------------------------------------

    @Test
    void generate_currentYear_matchesMtsaYyXxxPattern() {
        // Arrange: sequence returns 1 for the first booking
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(1L);

        int currentYear = LocalDate.now().getYear();

        // Act
        String reference = generator.generate(currentYear);

        // Assert: matches MTSA-YYXXX exactly
        Pattern pattern = Pattern.compile("^MTSA-\\d{2}\\d{3}$");
        assertThat(reference).matches(pattern);
    }

    @Test
    void generate_currentYear_twoDigitYearPrefixMatchesCurrentYear() {
        // Arrange
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(1L);

        int currentYear = LocalDate.now().getYear();
        int expectedYY = currentYear % 100;

        // Act
        String reference = generator.generate(currentYear);

        // Assert: the two-digit year portion (chars 5-6) equals currentYear % 100
        String yyPart = reference.substring(5, 7); // "MTSA-YY..." → index 5 and 6
        assertThat(Integer.parseInt(yyPart)).isEqualTo(expectedYY);
    }

    @Test
    void generate_sequenceNumberOne_producesThreeDigitZeroPaddedSuffix() {
        // Arrange: first booking of the year → sequence = 1
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(1L);

        int year = 2024;

        // Act
        String reference = generator.generate(year);

        // Assert: MTSA-24001
        assertThat(reference).isEqualTo("MTSA-24001");
    }

    @Test
    void generate_specificYear2025_prefixIs25() {
        // Arrange
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(42L);

        // Act
        String reference = generator.generate(2025);

        // Assert: MTSA-25042
        assertThat(reference).isEqualTo("MTSA-25042");
    }

    // -----------------------------------------------------------------------
    // Test 2: Sequential numbers increment correctly
    // -----------------------------------------------------------------------

    @Test
    void generate_sequentialCalls_sequenceNumbersIncrement() {
        // Arrange: simulate sequence returning 1, 2, 3 on successive calls
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);

        int year = 2024;

        // Act
        String ref1 = generator.generate(year);
        String ref2 = generator.generate(year);
        String ref3 = generator.generate(year);

        // Assert: each reference has a strictly increasing numeric suffix
        assertThat(ref1).isEqualTo("MTSA-24001");
        assertThat(ref2).isEqualTo("MTSA-24002");
        assertThat(ref3).isEqualTo("MTSA-24003");
    }

    @Test
    void generate_sequentialCalls_allReferencesAreDistinct() {
        // Arrange: sequence returns 10, 11, 12
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult())
                .thenReturn(10L)
                .thenReturn(11L)
                .thenReturn(12L);

        int year = 2024;

        // Act
        String ref1 = generator.generate(year);
        String ref2 = generator.generate(year);
        String ref3 = generator.generate(year);

        // Assert: all references are unique
        assertThat(ref1).isNotEqualTo(ref2);
        assertThat(ref2).isNotEqualTo(ref3);
        assertThat(ref1).isNotEqualTo(ref3);
    }

    @Test
    void generate_largeSequenceNumber_formatsCorrectly() {
        // Arrange: sequence number 999 (maximum 3-digit value)
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(999L);

        // Act
        String reference = generator.generate(2024);

        // Assert: MTSA-24999
        assertThat(reference).isEqualTo("MTSA-24999");
    }

    @Test
    void generate_sequenceNumberExceedsThreeDigits_stillFormatsWithFormat() {
        // Arrange: sequence number 1000 (overflows 3-digit padding — format uses %03d so it expands)
        when(entityManager.createNativeQuery(contains("nextval"))).thenReturn(nextvalQuery);
        when(nextvalQuery.getSingleResult()).thenReturn(1000L);

        // Act
        String reference = generator.generate(2024);

        // Assert: format expands beyond 3 digits — MTSA-241000
        assertThat(reference).isEqualTo("MTSA-241000");
        // The prefix is still correct
        assertThat(reference).startsWith("MTSA-24");
    }

    @Test
    void printHash() {
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
        String hash = encoder.encode("Admin@1234");
        System.out.println("HASH: " + hash);
        System.out.println("VALID: " + encoder.matches("Admin@1234", hash));
    }
}
