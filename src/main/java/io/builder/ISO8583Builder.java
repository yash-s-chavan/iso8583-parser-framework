package io.builder;

import io.config.FieldConfigurationManager;
import io.config.FieldDefinition;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ISO8583Builder {

    /**
     * Converts a mapped JSON object back into a raw ISO8583 Hex String.
     */
    private final FieldConfigurationManager configManager;

    public ISO8583Builder(FieldConfigurationManager configManager) {
        this.configManager = configManager;
    }

    public String buildRawString(JSONObject jsonPayload) {

        StringBuilder finalMessage = new StringBuilder();

        // 1. Get MTI (Assuming "0" or "MTI" is the key based on your samples)
        String mti = jsonPayload.optString("0", "0000");
        finalMessage.append(mti);

        // TODO: 2. Extract all Data Element keys from the JSON and sort them numerically

        long bitmap = 0;
        List<Integer> fields = new ArrayList<>();
        for(String str: jsonPayload.keySet()){
            try{
                int value = Integer.parseInt(str);
                if(value != 0) {
                    fields.add(value);
                    bitmap |= (1L << (64-value));
                }
            } catch(NumberFormatException e){
                throw new NumberFormatException();
            }

        }
        Collections.sort(fields);

        // TODO: 3. Generate the 64-bit binary bitmap, then convert to Hex
        String hexStr = String.format("%016X", bitmap);
        finalMessage.append(hexStr);
        // TODO: 4. Format and append each Data Element based on configuration rules
        for (int field : fields) {
            String rawValue = jsonPayload.getString(String.valueOf(field));
            FieldDefinition def = configManager.getDefinition(field);

            if (def == null) {
                throw new IllegalStateException("Missing configuration for field: " + field);
            }

            finalMessage.append(formatDataElement(rawValue, def, field));
        }

        return finalMessage.toString();
    }

    /**
     * Applies FIXED, LLVAR, or LLLVAR formatting rules to a raw string.
     */
    private String formatDataElement(String value, FieldDefinition def, int fieldNumber) {
        return switch (def.format().toUpperCase()) {
            case "FIXED" -> {
                if (value.length() != def.length()) {
                    // TODO: Implement padding logic (spaces for alphanumeric, zeros for numeric)
                    throw new IllegalArgumentException("Field " + fieldNumber + " length mismatch.");
                }
                yield value;
            }
            case "LLVAR" -> String.format("%02d%s", value.length(), value);
            case "LLLVAR" -> String.format("%03d%s", value.length(), value);
            default -> throw new IllegalArgumentException("Unknown format: " + def.format());
        };
    }
}