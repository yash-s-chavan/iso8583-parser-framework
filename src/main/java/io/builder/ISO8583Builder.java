package io.builder;

import io.config.FieldConfigurationManager;
import io.config.FieldDefinition;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ISO8583Builder {

    private final FieldConfigurationManager configManager;

    public ISO8583Builder(FieldConfigurationManager configManager) {
        this.configManager = configManager;
    }

    public String buildRawString(JSONObject jsonPayload) {
        StringBuilder finalMessage = new StringBuilder();

        // 1. Get MTI
        String mti = jsonPayload.optString("0", "0000");
        finalMessage.append(mti);

        // 2. Extract Data Element keys, sort them, and build the bitmaps
        long primaryBitmap = 0;
        long secondaryBitmap = 0;
        boolean hasSecondary = false;
        List<Integer> fields = new ArrayList<>();

        for (String key : jsonPayload.keySet()) {
            try {
                int value = Integer.parseInt(key);
                if (value > 0 && value <= 64) {
                    fields.add(value);
                    primaryBitmap |= (1L << (64 - value));
                } else if (value > 64 && value <= 128) {
                    fields.add(value);
                    hasSecondary = true;
                    secondaryBitmap |= (1L << (128 - value));
                }
            } catch (NumberFormatException e) {
                // Safely ignore non-numeric keys
            }
        }

        if (hasSecondary) {
            primaryBitmap |= (1L << 63);
        }

        Collections.sort(fields);

        // 3. Generate the binary bitmaps (Zero-padded to 16 hex chars)
        finalMessage.append(String.format("%016X", primaryBitmap));
        if (hasSecondary) {
            finalMessage.append(String.format("%016X", secondaryBitmap));
        }

        // 4. Format and append each Data Element based on configuration rules
        for (int field : fields) {
            // STRICT NULL CHECK ADDED HERE:
            if (jsonPayload.isNull(String.valueOf(field))) {
                throw new IllegalArgumentException("Payload contains explicit null for field: " + field);
            }

            String rawValue = jsonPayload.getString(String.valueOf(field));

            FieldDefinition def = configManager.getDefinition(field);
            if (def == null) {
                throw new IllegalStateException("Missing configuration for field: " + field);
            }

            finalMessage.append(formatDataElement(rawValue, def, field));
        }

        return finalMessage.toString();
    }

    private String formatDataElement(String value, FieldDefinition def, int fieldNumber) {
        int currentLength = value.length();
        int requiredLength = def.length();

        return switch (def.format().toUpperCase()) {
            case "FIXED" -> {
                if (currentLength > requiredLength) {
                    throw new IllegalArgumentException("Field " + fieldNumber + " length mismatch. Expected max " + requiredLength + " but got " + currentLength);
                } else if (currentLength < requiredLength) {
                    if (value.matches("\\d+")) {
                        yield String.format("%" + requiredLength + "s", value).replace(' ', '0');
                    } else {
                        yield String.format("%-" + requiredLength + "s", value);
                    }
                }
                yield value;
            }
            case "LLVAR" -> {
                if (currentLength > requiredLength) {
                    throw new IllegalArgumentException("Field " + fieldNumber + " exceeds max LLVAR length of " + requiredLength);
                }
                yield String.format("%02d%s", currentLength, value);
            }
            case "LLLVAR" -> {
                if (currentLength > requiredLength) {
                    throw new IllegalArgumentException("Field " + fieldNumber + " exceeds max LLLVAR length of " + requiredLength);
                }
                yield String.format("%03d%s", currentLength, value);
            }
            default -> throw new IllegalArgumentException("Unknown format: " + def.format());
        };
    }
}