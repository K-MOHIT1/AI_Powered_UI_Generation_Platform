package com.codingshuttle.promptic.workspace_service.dto.project;


import com.codingshuttle.promptic.common_lib.enums.ProjectRole;

import java.time.Instant;

public record ProjectSummaryResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        ProjectRole role
) {
}

