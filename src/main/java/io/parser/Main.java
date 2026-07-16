package io.parser;

import io.config.FieldConfigurationManager;
import io.reader.BinaryMessageReader;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point: parse an ISO 8583 binary (.bin) file and print
 * each message as pretty-printed JSON.
 *
 * Usage:
 *   java -cp <jar> io.parser.Main <path-to-file.bin> [--dump]
 *
 * Flags:
 *   --dump   Print a hex dump of the file before parsing (useful for debugging)
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <jar> io.parser.Main <path-to-file.bin> [--dump]");
            System.exit(1);
        }

        Path filePath = Path.of(args[0]);
        boolean hexDump = args.length > 1 && args[1].equalsIgnoreCase("--dump");

        if (!Files.exists(filePath)) {
            System.err.println("File not found: " + filePath.toAbsolutePath());
            System.exit(1);
        }

        try {
            // Optional: print hex dump before parsing
            if (hexDump) {
                BinaryMessageReader.hexDump(filePath, 256);
            }

            // 1. Read + convert binary messages to parser-ready strings
            BinaryMessageReader reader = new BinaryMessageReader();
            List<String> rawMessages = reader.readMessages(filePath);

            // 2. Set up the parser
            FieldConfigurationManager configManager = new FieldConfigurationManager();
            ISO8583Parser parser = new ISO8583Parser(configManager);

            // 3. Parse each message and print JSON
            System.out.println("===========================================");
            System.out.println("Parsed " + rawMessages.size() + " message(s) from: " + filePath.getFileName());
            System.out.println("===========================================\n");

            for (int i = 0; i < rawMessages.size(); i++) {
                System.out.println("--- Message " + (i + 1) + " ---");
                System.out.println("Raw (hex): " + rawMessages.get(i).substring(0, Math.min(60, rawMessages.get(i).length())) + "...");
                try {
                    JSONObject parsed = parser.parse(rawMessages.get(i));
                    System.out.println("Parsed JSON:\n" + parsed.toString(2));
                } catch (Exception e) {
                    System.err.println("Failed to parse message " + (i + 1) + ": " + e.getMessage());
                }
                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}