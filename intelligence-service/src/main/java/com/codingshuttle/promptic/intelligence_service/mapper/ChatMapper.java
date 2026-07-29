package com.codingshuttle.promptic.intelligence_service.mapper;

import com.codingshuttle.promptic.intelligence_service.dto.chat.ChatResponse;
import com.codingshuttle.promptic.intelligence_service.entity.ChatMessage;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    List<ChatResponse> fromListOfChatMessage(List<ChatMessage> chatMessageList);
}

