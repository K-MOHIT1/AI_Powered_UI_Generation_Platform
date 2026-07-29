package com.codingshuttle.promptic.workspace_service.dto.member;

import com.codingshuttle.promptic.common_lib.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull ProjectRole role) {
}

