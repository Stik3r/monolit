package com.filescloud.monolit.mappers;

import com.filescloud.monolit.models.dtos.FileDto;
import com.filescloud.monolit.models.entity.File;

public class FileDtoMapper {

    public static FileDto toFileDto(File file) {
        FileDto fileDto = new FileDto();

        fileDto.setName(file.fileName);
        fileDto.setFolder(file.isFolder);
        fileDto.setSize(file.fileSize);
        fileDto.setParentId(file.parentId);
        fileDto.setModified(file.modified);

        return fileDto;
    }
}
