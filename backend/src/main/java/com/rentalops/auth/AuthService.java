package com.rentalops.auth;

import com.rentalops.common.NotFoundException;
import com.rentalops.user.Role;
import com.rentalops.user.RoleName;
import com.rentalops.user.RoleRepository;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import com.rentalops.user.UserResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetTokenRepository resetTokenRepository;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            PasswordResetTokenRepository resetTokenRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.resetTokenRepository = resetTokenRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (request.role() == RoleName.ADMIN) {
            throw new IllegalArgumentException("Admin users must be seeded or created internally");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalArgumentException("Role does not exist"));
        User user = new User(
                request.fullName(),
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.phone());
        user.addRole(role);
        User saved = userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(saved), UserResponse.from(saved));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        loginAttemptService.assertNotBlocked(email);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(email);
            throw ex;
        } catch (AuthenticationException ex) {
            throw ex;
        }
        loginAttemptService.reset(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    /** Always succeeds (does not reveal whether the email exists). Returns a token in dev. */
    @Transactional
    public String requestPasswordReset(String email) {
        String normalized = email.toLowerCase();
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            return null;
        }
        String token = randomToken();
        resetTokenRepository.save(new PasswordResetToken(user, token, Instant.now().plus(RESET_TTL)));
        log.info("Password reset requested for {} — token: {}", normalized, token);
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken entry = resetTokenRepository.findByToken(token).orElse(null);
        if (entry == null || !entry.isUsable(Instant.now())) {
            throw new IllegalArgumentException("This reset link is invalid or has expired");
        }
        User user = userRepository.findById(entry.getUser().getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.changePassword(passwordEncoder.encode(newPassword));
        entry.markUsed();
        loginAttemptService.reset(user.getEmail());
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
