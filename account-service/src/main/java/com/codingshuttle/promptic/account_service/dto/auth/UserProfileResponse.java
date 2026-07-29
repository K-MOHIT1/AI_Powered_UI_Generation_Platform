package com.codingshuttle.promptic.account_service.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String name
) {
}

