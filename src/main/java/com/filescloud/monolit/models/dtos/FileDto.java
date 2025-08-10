package com.filescloud.monolit.models.dtos;

import java.time.LocalDateTime;

public class FileDto {
    public String name;
    public boolean isFolder;
    public LocalDateTime modified;
    public Long size;
    public Long parentId;

    public FileDto(String s, boolean b, LocalDateTime now) {
        name = s;
        isFolder = b;
        modified = now;

    }

    public FileDto() {}
}
