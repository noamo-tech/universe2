package de.noamo.universe2.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NHRController {

    @RequestMapping("/nhr")
    public String overview(){
        return "Welcome to NHR API";
    }
}
