package io.parser.controller;

import io.builder.ISO8583Builder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/iso8583")
public class ISO8583Controller {

    private final ISO8583Builder iso8583Builder;

    // Constructor injection is best practice in Spring
    public ISO8583Controller(ISO8583Builder iso8583Builder) {
        this.iso8583Builder = iso8583Builder;
    }

    @PostMapping("/build")
    public ResponseEntity<String> buildIsoString(@RequestBody Map<String, String> payload) {
        try {
            // Uses the convenience overload to build the raw string
            String rawIsoMessage = iso8583Builder.buildRawString(payload);
            return ResponseEntity.ok(rawIsoMessage);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Catches the exceptions thrown by your builder's validations
            return ResponseEntity.badRequest().body("Error building message: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An unexpected error occurred.");
        }
    }
}