package com.rentalops.user;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> list(
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return userService.list(role, status, q, pageable);
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody AdminUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable Long id, @RequestParam UserStatus value) {
        return userService.setStatus(id, value);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id) {
        return Map.of("temporaryPassword", userService.resetPassword(id));
    }
}
