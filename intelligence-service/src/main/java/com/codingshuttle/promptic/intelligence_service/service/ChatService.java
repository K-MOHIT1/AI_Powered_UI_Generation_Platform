package com.codingshuttle.promptic.intelligence_service.service;


import com.codingshuttle.promptic.intelligence_service.dto.chat.ChatResponse;

import java.util.List;

public interface ChatService {

    List<ChatResponse> getProjectChatHistory(Long projectId);
}

