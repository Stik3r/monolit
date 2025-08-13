package com.filescloud.monolit.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.upload.MultiFileReceiver;
import com.vaadin.flow.component.upload.Receiver;
import com.vaadin.flow.component.upload.Upload;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class UploadArea extends Div {

    private final Upload uploadField;
    private final Span errorField;

    public UploadArea(File uploadFolder) {
        setClassName("upload-area");
        uploadField = new Upload(createFileReceiver(uploadFolder));
        uploadField.setMaxFiles(100);
        // set max file size to 1 MB
        uploadField.setMaxFileSize(1 * 1024 * 1024);
        uploadField.setDropLabel(new Label("Drop file here (max 1MB)"));
        uploadField.setHeight("100%");
        uploadField.setWidth("100%");
        uploadField.getStyle()
                        .set("background", "red");

        setHeight("25%");
        setWidth("100%");


        errorField = new Span();
        errorField.setVisible(false);
        errorField.getStyle().set("color", "red");

        uploadField.addFailedListener(e -> showErrorMessage(e.getReason().getMessage()));
        uploadField.addFileRejectedListener(e -> showErrorMessage(e.getErrorMessage()));

        add(uploadField, errorField);
    }

    public Upload getUploadField() {
        return uploadField;
    }

    public void hideErrorField() {
        errorField.setVisible(false);
    }

    private Receiver createFileReceiver(File uploadFolder) {
        return (MultiFileReceiver) (filename, mimeType) -> {
            File file = new File(uploadFolder, filename);
            try {
                return new FileOutputStream(file);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                return null;
            }
        };
    }

    private void showErrorMessage(String message) {
        errorField.setVisible(true);
        errorField.setText(message);
    }
}
