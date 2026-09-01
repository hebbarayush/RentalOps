package com.rentalops.auth;

import com.rentalops.user.UserResponse;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUserService.requireCurrentUser());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody PasswordResetRequests.Forgot request) {
        String token = authService.requestPasswordReset(request.email());
        Map<String, Object> body = new HashMap<>();
        body.put("message", "If that email is registered, a reset link has been sent.");
        // Dev convenience only — no mail server is configured.
        if (token != null) {
            body.put("devToken", token);
        }
        return body;
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody PasswordResetRequests.Reset request) {
        authService.resetPassword(request.token(), request.newPassword());
        return Map.of("message", "Password updated. You can now sign in.");
    }
}
