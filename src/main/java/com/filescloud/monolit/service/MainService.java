package com.filescloud.monolit.service;

import com.filescloud.monolit.models.dtos.FileDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MainService {

    FileStorageService fileStorageService;

    public MainService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public List<FileDto> getFiles(Long parentId) {
        return fileStorageService.getFiles(parentId);
    }
}
