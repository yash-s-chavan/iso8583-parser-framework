package io.builder;
import io.config.FieldConfigurationManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ISO8583BuilderTest {

    private ISO8583Builder builder;

    @BeforeEach
    void setUp() {
        // 1. Initialize the configuration manager (loads fields.json)
        FieldConfigurationManager configManager = new FieldConfigurationManager();

        // 2. Inject it into a new instance of the Builder
        builder = new ISO8583Builder(configManager);
    }

    @Test
    void testBuildRawString_Success() {
        // Given: A sample JSON payload
        JSONObject mockPayload = new JSONObject();
        mockPayload.put("0", "0200");
        // ... add more mock fields based on your test payloads

        // When: We call the INSTANCE method (not static)
        String rawIsoString = builder.buildRawString(mockPayload);

        // Then: Assert the expected raw string output
        // assertEquals("0200...", rawIsoString);
    }
}