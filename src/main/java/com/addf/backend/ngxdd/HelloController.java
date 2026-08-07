package com.addf.backend.ngxdd;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Basic liveness check")
public class HelloController {

    @Operation(summary = "Sanity-check endpoint")
    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }

}
