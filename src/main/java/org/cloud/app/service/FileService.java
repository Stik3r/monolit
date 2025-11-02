package org.cloud.app.service;

import org.cloud.app.dao.FileRepository;
import org.cloud.app.models.entity.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileService {

    @Autowired
    FileRepository fileRepository;

    public List<File> getRootFiles(){
        return fileRepository.findByParentId(null);
    }
}
