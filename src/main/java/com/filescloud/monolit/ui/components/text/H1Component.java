package com.filescloud.monolit.ui.components.text;

import com.vaadin.flow.component.html.H1;

public class H1Component extends H1 {

    public H1Component(String text) {
        super(text);

        this.getStyle().set("color", "var(--lumo-primary-color)");
        this.getStyle().set("font-weight", "bold");
        this.addClassNames("mx-m", "my-m");
    }
}
