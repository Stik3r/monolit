package com.filescloud.monolit.mappers;

import com.filescloud.monolit.models.dtos.FileDto;
import com.filescloud.monolit.models.entity.File;

public class FileDtoMapper {

    public static FileDto toFileDto(File file) {
        FileDto fileDto = new FileDto();

        fileDto.name = file.fileName;
        fileDto.isFolder = file.isFolder;
        fileDto.size = file.fileSize;
        fileDto.parentId = file.parentId;
        fileDto.modified = file.modified;

        return fileDto;
    }
}
