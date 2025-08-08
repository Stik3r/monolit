package com.filescloud.monolit.dao;

import com.filescloud.monolit.models.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findAllByParentIdIsNull();

    List<File> findAllByParentId(Long id);
}
