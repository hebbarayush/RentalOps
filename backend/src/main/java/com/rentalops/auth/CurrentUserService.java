package com.rentalops.auth;

import com.rentalops.common.NotFoundException;
import com.rentalops.user.RoleName;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
    }

    public boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == roleName);
    }

    public boolean isAdmin(User user) {
        return hasRole(user, RoleName.ADMIN);
    }

    public boolean isManagerOrAdmin(User user) {
        return hasRole(user, RoleName.ADMIN) || hasRole(user, RoleName.PROPERTY_MANAGER);
    }

    public boolean isTenant(User user) {
        return hasRole(user, RoleName.TENANT);
    }
}
