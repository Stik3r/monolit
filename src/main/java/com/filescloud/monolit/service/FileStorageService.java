package com.filescloud.monolit.service;

import com.filescloud.monolit.dao.FileRepository;
import com.filescloud.monolit.mappers.FileDtoMapper;
import com.filescloud.monolit.models.dtos.FileDto;
import com.filescloud.monolit.models.entity.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileStorageService {

    @Autowired
    private FileRepository fileRepository;

    public List<FileDto> getFiles(Long parentId) {
        List<File> files;
        if (parentId == null) {
            files = fileRepository.findAllByParentIdIsNull();
        }
        else {
            files = fileRepository.findAllByParentId(parentId);
        }
        return files.stream().map(FileDtoMapper::toFileDto).toList();
    }
}
