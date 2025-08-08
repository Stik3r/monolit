package com.filescloud.monolit.ui.view;


import com.filescloud.monolit.ui.components.AppNavbarComponent;
import com.filescloud.monolit.ui.components.FileManagerComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("/filestorage")
public class FileStorageView extends AppLayout {
    public FileStorageView() {
        addToNavbar(new DrawerToggle());
        addToDrawer(new AppNavbarComponent());
        setContent(createMainContent());
    }

    private Component createMainContent() {
        // Основной контент с FileManagerComponent
        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setPadding(false);

        // Добавляем наш компонент управления файлами
        FileManagerComponent fileManager = new FileManagerComponent();
        content.add(fileManager);
        content.setFlexGrow(1, fileManager);

        return content;
    }
}
