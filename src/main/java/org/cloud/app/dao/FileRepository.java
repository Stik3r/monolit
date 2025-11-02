package org.cloud.app.dao;

import org.cloud.app.models.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {

    File findByFileid(Long id);

    List<File> findByParentId(Long parentId);
}
