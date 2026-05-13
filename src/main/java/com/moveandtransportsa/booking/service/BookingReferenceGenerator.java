package com.moveandtransportsa.booking.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingReferenceGenerator {

    @PersistenceContext
    private EntityManager em;

    /**
     * Generates a unique booking reference for the given year.
     * Uses a per-year PostgreSQL sequence to guarantee uniqueness under concurrent load.
     * Format: MTSA-YYXXX (e.g. MTSA-24001)
     */
    @Transactional
    public String generate(int year) {
        String seqName = "booking_ref_seq_" + year;
        em.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS " + seqName + " START 1 INCREMENT 1")
                .executeUpdate();
        Long next = ((Number) em.createNativeQuery("SELECT nextval('" + seqName + "')")
                .getSingleResult()).longValue();
        return String.format("MTSA-%02d%03d", year % 100, next);
    }
}
