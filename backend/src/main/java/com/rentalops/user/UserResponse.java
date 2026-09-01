package com.rentalops.user;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Set<RoleName> roles,
        UserStatus status,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
