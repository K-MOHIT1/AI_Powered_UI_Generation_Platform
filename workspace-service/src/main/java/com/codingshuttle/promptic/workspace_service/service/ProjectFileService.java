package com.codingshuttle.promptic.workspace_service.service;


import com.codingshuttle.promptic.common_lib.dto.FileTreeDto;
import com.codingshuttle.promptic.workspace_service.dto.project.FileContentResponse;

public interface ProjectFileService {
    FileTreeDto getFileTree(Long projectId);

    String getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);
}

