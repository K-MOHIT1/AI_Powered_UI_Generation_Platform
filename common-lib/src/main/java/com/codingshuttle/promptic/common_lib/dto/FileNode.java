package com.codingshuttle.promptic.common_lib.dto;


public record FileNode(
        String path
) {

    @Override
    public String toString() {
        return path;
    }
}
