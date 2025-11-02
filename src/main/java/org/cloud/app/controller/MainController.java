package org.cloud.app.controller;

import org.cloud.app.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class MainController {

    @Autowired
    FileService fileService;

    @GetMapping
    @RequestMapping("/")
    public String redirect(Model model){
        return "redirect:/home";
    }

    @GetMapping
    @RequestMapping("/home")
    public String home(Model model){
        model.addAttribute("files", fileService.getRootFiles());
        return "home";
    }
}
