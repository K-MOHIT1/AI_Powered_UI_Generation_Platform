package com.codingshuttle.promptic.workspace_service.controller;

import com.codingshuttle.promptic.common_lib.dto.FileTreeDto;
import com.codingshuttle.promptic.workspace_service.dto.project.FileContentResponse;
import com.codingshuttle.promptic.workspace_service.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/files")
public class FileController {

    private final ProjectFileService projectFileService;

    @GetMapping
    public ResponseEntity<FileTreeDto> getFileTree(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectFileService.getFileTree(projectId));
    }

    @GetMapping("/content")
    public ResponseEntity<FileContentResponse> getFile(
            @PathVariable Long projectId,
            @RequestParam String path) {
        String content = projectFileService.getFileContent(projectId, path);
        return ResponseEntity.ok(new FileContentResponse(path, content));
    }

}

