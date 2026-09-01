package com.rentalops.user;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.common.SpecFilters;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-only management of user accounts. */
@Service
public class UserService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(RoleName role, UserStatus status, String q, Pageable pageable) {
        requireAdmin();
        List<Specification<User>> specs = new ArrayList<>();
        if (role != null) {
            specs.add((root, cq, cb) -> cb.equal(root.join("roles").get("name"), role));
        }
        if (status != null) {
            specs.add((root, cq, cb) -> cb.equal(root.get("status"), status));
        }
        if (SpecFilters.has(q)) {
            String like = SpecFilters.like(q);
            specs.add((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like)));
        }
        return userRepository.findAll(SpecFilters.combine(specs), pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse create(AdminUserRequest request) {
        requireAdmin();
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (request.role() == RoleName.TENANT) {
            throw new IllegalArgumentException("Create tenant accounts through the tenant record, or self-registration");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalArgumentException("Role does not exist"));
        User user = new User(
                request.fullName(),
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.phone());
        user.addRole(role);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse setStatus(Long id, UserStatus status) {
        requireAdmin();
        User user = require(id);
        if (user.getId().equals(currentUserService.requireCurrentUser().getId())) {
            throw new IllegalArgumentException("You cannot change your own account status");
        }
        user.setStatus(status);
        return UserResponse.from(user);
    }

    /** Resets the password to a generated value which is returned once to the admin. */
    @Transactional
    public String resetPassword(Long id) {
        requireAdmin();
        User user = require(id);
        String temp = randomPassword();
        user.changePassword(passwordEncoder.encode(temp));
        return temp;
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
