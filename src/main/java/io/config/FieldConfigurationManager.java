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

            // 1. Parse the root JSON
            JSONObject rootJson = new JSONObject(new JSONTokener(is));

            // 2. Step inside the "fields" object
            JSONObject fieldsJson = rootJson.getJSONObject("fields");

            for (String key : fieldsJson.keySet()) {
                int fieldNumber = Integer.parseInt(key);
                JSONObject fieldProps = fieldsJson.getJSONObject(key);

                String name = fieldProps.getString("name");
                String format = fieldProps.getString("format");

                // 3. Intelligently grab length or max_length
                int length = fieldProps.has("length") ?
                        fieldProps.getInt("length") :
                        fieldProps.getInt("max_length");

                tempMap.put(fieldNumber, new FieldDefinition(name, format, length));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ISO 8583 field configurations", e);
        }

        return Collections.unmodifiableMap(tempMap);
    }

    public FieldDefinition getDefinition(int field) {
        return fieldDefinitions.get(field);
    }
}