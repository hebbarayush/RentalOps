package com.rentalops.auth;

import com.rentalops.user.UserResponse;

public record AuthResponse(String token, UserResponse user) {
}

