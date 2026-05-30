package com.vanguard.alert.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AlertDashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }
}
