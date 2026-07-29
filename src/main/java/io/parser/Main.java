package io.parser;

import io.config.FieldConfigurationManager;
import io.reader.BinaryMessageReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Usage: java -cp <jar> io.parser.Main <path-to-file.bin> [--dump]");
            System.exit(1);
        }

        Path filePath = Path.of(args[0]);
        boolean hexDump = args.length > 1 && args[1].equalsIgnoreCase("--dump");

        if (!Files.exists(filePath)) {
            logger.error("File not found: {}", filePath.toAbsolutePath());
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
            logger.info("===========================================");
            logger.info("Parsed {} message(s) from: {}", rawMessages.size(), filePath.getFileName());
            logger.info("===========================================");

            for (int i = 0; i < rawMessages.size(); i++) {
                logger.info("--- Message {} ---", i + 1);
                logger.info("Raw (hex): {}...", rawMessages.get(i).substring(0, Math.min(60, rawMessages.get(i).length())));
                try {
                    Map<String, String> parsed = parser.parse(rawMessages.get(i));
                    logger.info("Parsed JSON:\n{}", toSortedJson(parsed));
                } catch (Exception e) {
                    logger.error("Failed to parse message {}: {}", i + 1, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error: ", e);
            System.exit(1);
        }
    }

    /**
     * Serializes a field map to a pretty-printed JSON string, preserving the map's iteration order.
     * This avoids JSONObject which internally uses a HashMap and would scramble the field order.
     */
    private static String toSortedJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{\n");
        int count = 0;
        int total = fields.size();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": \"")
              .append(entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""))
              .append("\"");
            if (++count < total) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}