package com.codingshuttle.promptic.workspace_service.dto.member;


import com.codingshuttle.promptic.common_lib.enums.ProjectRole;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String username,
        String name,
        ProjectRole projectRole,
        Instant invitedAt
) {
}

