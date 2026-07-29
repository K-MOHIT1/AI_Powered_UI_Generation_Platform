package com.codingshuttle.promptic.workspace_service.mapper;

import com.codingshuttle.promptic.common_lib.dto.FileNode;
import com.codingshuttle.promptic.workspace_service.entity.ProjectFile;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectFileMapper {

    List<FileNode> toListOfFileNode(List<ProjectFile> projectFileList);
}

