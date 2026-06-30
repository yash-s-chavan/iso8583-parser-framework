package io.config;
import io.parser.ISO8583Parser;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FieldConfigurationManager {

    private final Map<Integer, FieldDefinition> fieldDefinitions;

    public FieldConfigurationManager() {
        this.fieldDefinitions = loadConfiguration("config/fields.json");
    }

    private Map<Integer, FieldDefinition> loadConfiguration(String resourcePath) {
        Map<Integer, FieldDefinition> tempMap = new HashMap<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Configuration file not found: " + resourcePath);
            }

            JSONObject jsonConfig = new JSONObject(new JSONTokener(is));

            for (String key : jsonConfig.keySet()) {
                int fieldNumber = Integer.parseInt(key);
                JSONObject fieldProps = jsonConfig.getJSONObject(key);

                String format = fieldProps.getString("format");
                int length = fieldProps.getInt("length");

                tempMap.put(fieldNumber, new FieldDefinition(format, length));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ISO 8583 field configurations", e);
        }

        // Return an unmodifiable map to ensure thread safety across builder instances
        return Collections.unmodifiableMap(tempMap);
    }

    public FieldDefinition getDefinition(int field) {
        return fieldDefinitions.get(field);
    }
}