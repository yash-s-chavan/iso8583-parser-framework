package io.parser;

import io.config.FieldConfigurationManager;
import io.config.FieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;

public class ISO8583Parser {

    private final FieldConfigurationManager configManager;

    // Inject the exact same configuration manager used by the builder
    public ISO8583Parser(FieldConfigurationManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Parses a raw ISO 8583 message string into a field map sorted by field number.
     * Keys are field numbers as strings ("0", "2", "3", …); values are the extracted strings.
     * The returned LinkedHashMap preserves numerically ascending insertion order.
     */
    public Map<String, String> parse(String rawMessage) {
        // TreeMap sorts keys numerically; we then transfer to LinkedHashMap to preserve that order.
        TreeMap<String, String> sortedData = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        int pointer = 0;

        try {
            // 1. Extract MTI (First 4 characters)
            String mti = rawMessage.substring(pointer, pointer + 4);
            sortedData.put("0", mti);  // field 0 = MTI
            pointer += 4;

            // 2. Extract Primary Bitmap (Next 16 hex characters)
            String primaryBitmapHex = rawMessage.substring(pointer, pointer + 16);
            pointer += 16;
            // Parse unsigned long to handle high-bit flags (like Bit 1)
            long primaryBitmap = Long.parseUnsignedLong(primaryBitmapHex, 16);

            List<Integer> presentFields = new ArrayList<>();
            boolean hasSecondary = (primaryBitmap & (1L << 63)) != 0;

            // Decode primary bitmap (Fields 1-64)
            for (int i = 1; i <= 64; i++) {
                if ((primaryBitmap & (1L << (64 - i))) != 0) {
                    presentFields.add(i);
                }
            }

            // 3. Extract Secondary Bitmap (If Bit 1 was flipped)
            if (hasSecondary) {
                String secondaryBitmapHex = rawMessage.substring(pointer, pointer + 16);
                pointer += 16;
                long secondaryBitmap = Long.parseUnsignedLong(secondaryBitmapHex, 16);

                // Decode secondary bitmap (Fields 65-128)
                for (int i = 1; i <= 64; i++) {
                    if ((secondaryBitmap & (1L << (64 - i))) != 0) {
                        presentFields.add(i + 64);
                    }
                }

                // Remove Field 1 from our data element list, as it's just the bitmap flag itself
                presentFields.remove(Integer.valueOf(1));
            }

            // 4. The Sliding Window: Extract Data Elements
            for (int field : presentFields) {
                FieldDefinition def = configManager.getDefinition(field);
                if (def == null) {
                    throw new IllegalStateException("Missing configuration for field: " + field);
                }

                String format = def.format().toUpperCase();
                int maxLength = def.length();
                String extractedValue;

                switch (format) {
                    case "FIXED" -> {
                        extractedValue = rawMessage.substring(pointer, pointer + maxLength);
                        pointer += maxLength;
                    }
                    case "LLVAR" -> {
                        // Read 2-digit length header, then extract that many chars
                        int lengthHeader = Integer.parseInt(rawMessage.substring(pointer, pointer + 2));
                        pointer += 2;

                        if (lengthHeader > maxLength) {
                            throw new IllegalArgumentException("Field " + field + " declares length " + lengthHeader + " which exceeds max " + maxLength);
                        }

                        extractedValue = rawMessage.substring(pointer, pointer + lengthHeader);
                        pointer += lengthHeader;
                    }
                    case "LLLVAR" -> {
                        // Read 3-digit length header, then extract that many chars
                        int lengthHeader = Integer.parseInt(rawMessage.substring(pointer, pointer + 3));
                        pointer += 3;

                        if (lengthHeader > maxLength) {
                            throw new IllegalArgumentException("Field " + field + " declares length " + lengthHeader + " which exceeds max " + maxLength);
                        }

                        extractedValue = rawMessage.substring(pointer, pointer + lengthHeader);
                        pointer += lengthHeader;
                    }
                    default -> throw new IllegalArgumentException("Unknown format: " + format);
                }

                sortedData.put(String.valueOf(field), extractedValue);  // numerically ordered by TreeMap
            }

        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Message parsing failed. Reached end of string prematurely at pointer index: " + pointer, e);
        }

        // Transfer to LinkedHashMap so the caller receives a stable, insertion-ordered map.
        return new LinkedHashMap<>(sortedData);
    }

}