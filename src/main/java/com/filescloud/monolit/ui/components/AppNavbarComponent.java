package com.filescloud.monolit.ui.components;

import com.filescloud.monolit.ui.components.text.H1Component;
import com.filescloud.monolit.ui.view.FileStorageView;
import com.filescloud.monolit.ui.view.SettingsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

public class AppNavbarComponent extends VerticalLayout {

    public AppNavbarComponent() {
        createNavbarContent();
    }

    private void createNavbarContent() {

        H1Component title = new H1Component("MONOLIT");
        title.addClassName("navbar-title");

        RouterLink fileLink = createNavLink("Файлы", VaadinIcon.FOLDER, FileStorageView.class);
        RouterLink settingsLink = createNavLink("Настройки", VaadinIcon.COG, SettingsView.class);


        add(title, new Hr(),
                fileLink, settingsLink);
    }

    private RouterLink createNavLink(String text, VaadinIcon icon, Class<? extends Component> navigationTarget) {
        RouterLink link = new RouterLink();
        Icon iconComponent = icon.create();
        iconComponent.addClassNames(LumoUtility.Margin.Right.XSMALL);
        link.add(iconComponent);
        link.add(text);
        link.setRoute(navigationTarget);
        link.addClassName("nav-link");
        return link;
    }
}
