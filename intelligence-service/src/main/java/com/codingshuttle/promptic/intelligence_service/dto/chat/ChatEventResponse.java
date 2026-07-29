package com.codingshuttle.promptic.intelligence_service.dto.chat;


import com.codingshuttle.promptic.common_lib.enums.ChatEventType;

public record ChatEventResponse(
        Long id,
        ChatEventType type,
        Integer sequenceOrder,
        String content,
        String filePath,
        String metadata
) {
}

