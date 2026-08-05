package io.parser.controller;

import io.config.FieldConfigurationManager;
import io.parser.ISO8583Parser;
import io.reader.BinaryMessageReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ParserController {

    private final ISO8583Parser parser;

    public ParserController() {
        FieldConfigurationManager configManager = new FieldConfigurationManager();
        this.parser = new ISO8583Parser(configManager);
    }

    @PostMapping("/parse/hex")
    public ResponseEntity<?> parseHexString(@RequestBody Map<String, String> payload) {
        String hexString = payload.get("hex");
        if (hexString == null || hexString.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'hex' field in JSON request"));
        }
        try {
            Map<String, String> parsed = parser.parse(hexString.trim());
            return ResponseEntity.ok(parsed);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/parse/file")
    public ResponseEntity<?> parseFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file uploaded"));
        }
        try {
            Path tempFile = Files.createTempFile("iso8583-", ".bin");
            file.transferTo(tempFile.toFile());

            BinaryMessageReader reader = new BinaryMessageReader();
            List<String> rawMessages = reader.readMessages(tempFile);
            
            List<Map<String, String>> results = new ArrayList<>();
            for (String raw : rawMessages) {
                results.add(parser.parse(raw));
            }
            
            Files.deleteIfExists(tempFile);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
