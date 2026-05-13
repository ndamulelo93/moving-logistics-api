package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.dto.request.CreateAdminUserRequest;
import com.moveandtransportsa.booking.dto.request.UpdateAdminUserRequest;
import com.moveandtransportsa.booking.dto.response.AdminUserResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new admin user.
     * Throws {@link ConflictException} if the username is already taken.
     */
    @Transactional
    public AdminUserResponse createUser(CreateAdminUserRequest request) {
        if (adminUserRepository.findByUsername(request.username()).isPresent()) {
            throw new ConflictException("Username already taken: " + request.username());
        }

        AdminUser user = AdminUser.builder()
                .username(request.username())
                .displayName(request.displayName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build();

        AdminUser saved = adminUserRepository.save(user);
        return toResponse(saved);
    }

    /**
     * Updates an existing admin user.
     * Only non-null fields in the request are applied.
     * Throws {@link BookingNotFoundException} if the user does not exist.
     */
    @Transactional
    public AdminUserResponse updateUser(Long id, UpdateAdminUserRequest request) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Admin user not found with id: " + id));

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }

        AdminUser saved = adminUserRepository.save(user);
        return toResponse(saved);
    }

    /**
     * Soft-deletes an admin user by setting active=false.
     * There is no hard DELETE endpoint.
     * Throws {@link BookingNotFoundException} if the user does not exist.
     */
    @Transactional
    public void deactivateUser(Long id) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Admin user not found with id: " + id));
        user.setActive(false);
        adminUserRepository.save(user);
    }

    /**
     * Returns a paginated list of all admin users.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        return adminUserRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Returns a single admin user by ID.
     * Throws {@link BookingNotFoundException} if the user does not exist.
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Admin user not found with id: " + id));
        return toResponse(user);
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
    }
}
