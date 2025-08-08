package com.filescloud.monolit.models.dtos;

import java.time.LocalDateTime;

public class FileDto {
    public String name;
    public boolean isFolder;
    public LocalDateTime modified;
    public Long size;
    public Long parentId;
}
