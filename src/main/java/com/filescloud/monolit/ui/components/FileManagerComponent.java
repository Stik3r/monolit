package com.filescloud.monolit.ui.components;

import com.filescloud.monolit.models.dtos.FileDto;
import com.filescloud.monolit.service.FileStorageService;
import com.filescloud.monolit.service.MainService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@UIScope
public class FileManagerComponent extends Div {

    Grid<FileDto> grid;
    List<FileDto> items;

    MainService mainService;

    public FileManagerComponent(MainService mainService) {
        this.mainService = mainService;
        setSizeFull();
        // Инициализация тестовых данных
        items = new ArrayList<>();
        items.add(new FileDto("Документы", true, LocalDateTime.now()));
        items.add(new FileDto("Изображения", true, LocalDateTime.now()));
        items.add(new FileDto("report.pdf", false, LocalDateTime.now()));
        items.add(new FileDto("presentation.pptx", false, LocalDateTime.now()));

        //items = mainService.getFiles(null);

        createGrid();
        add(grid);
    }

    private void createGrid() {
        grid = new Grid<>();
        grid.setItems(items);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setSizeFull();

        // Колонка с иконкой и названием
        grid.addComponentColumn(item -> {
            Icon icon = item.isFolder
                    ? VaadinIcon.FOLDER.create()
                    : VaadinIcon.FILE.create();

            icon.setColor(
                    item.isFolder ? "#1976D2" : "#757575"
            );

            Span name = new Span(item.name);
            name.addClassNames(
                    LumoUtility.Margin.Left.SMALL,
                    LumoUtility.FontSize.MEDIUM
            );

            HorizontalLayout layout = new HorizontalLayout(icon, name);
            layout.addClassNames(
                    LumoUtility.AlignItems.CENTER,
                    LumoUtility.Padding.Horizontal.SMALL
            );
            return layout;
        }).setHeader("Имя").setAutoWidth(true).setFlexGrow(1);

        // Колонка с датой изменения
        grid.addColumn(item ->
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(item.modified))
                .setHeader("Изменен")
                .setWidth("12em");

        // Колонка с размером
        grid.addColumn(item -> item.size)
                .setHeader("Размер")
                .setWidth("8em")
                .addClassNames(
                        LumoUtility.TextAlignment.RIGHT,
                        LumoUtility.Padding.Horizontal.MEDIUM
                );

        // Обработка клика по папке
        grid.addItemClickListener(event -> {
            if (event.getItem().isFolder) {
                // Логика перехода в папку
                Notification.show("Открываем папку: " + event.getItem().name);
            }
        });

        grid.addClassNames(
                LumoUtility.Border.ALL,
                LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM
        );

    }

    public void setItems(List<FileDto> items) {
        this.items = items;
        grid.setItems(items);
    }
}
