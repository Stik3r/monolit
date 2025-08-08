package com.filescloud.monolit.controller;


import com.filescloud.monolit.ui.view.FileStorageView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

@Route("/")
public class RedierectController extends AppLayout implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo(FileStorageView.class);
    }
}
