package com.codingshuttle.promptic.account_service.dto.auth;

public record AuthResponse(
        String token,
        UserProfileResponse user
) {

}

