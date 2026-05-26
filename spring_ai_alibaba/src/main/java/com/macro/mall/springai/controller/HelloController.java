package com.macro.mall.springai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
@Tag(name = "HelloController", description = "HelloController")
public class HelloController {

    @GetMapping
    @Operation(summary = "hello world")
    public String hello() {
        return "hello world";
    }
}
