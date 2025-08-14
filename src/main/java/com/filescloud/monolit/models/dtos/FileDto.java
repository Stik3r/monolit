package com.filescloud.monolit.models.dtos;

import java.time.LocalDateTime;

public class FileDto {
    private String name;
    private boolean isFolder;
    private LocalDateTime modified;
    private Long size;
    private Long parentId;

    public FileDto(String s, boolean b, LocalDateTime now) {
        name = s;
        isFolder = b;
        modified = now;

    }

    public FileDto() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public void setFolder(boolean folder) {
        isFolder = folder;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
