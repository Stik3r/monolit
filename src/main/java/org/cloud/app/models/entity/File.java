package org.cloud.app.models.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_dbt")
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fileid")
    public Long fileid;

    @Column(name = "filename")
    public String fileName;

    @Column(name = "filesize")
    public Long fileSize;

    @Column(name = "isfolder")
    public boolean isFolder;

    @Column(name = "parentid")
    public Long parentId;

    @Column(name = "modified")
    public LocalDateTime modified;

    public boolean isFolder() {
        return isFolder;
    }
}