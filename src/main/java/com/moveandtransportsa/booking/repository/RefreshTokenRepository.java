package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.auth.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByFamilyId(String familyId);

    void deleteByAdminUserId(Long adminUserId);
}
