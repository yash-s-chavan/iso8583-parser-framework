package io.builder;

import io.config.FieldConfigurationManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ISO8583BuilderTest {

    private ISO8583Builder builder;

    @BeforeEach
    void setUp() {
        FieldConfigurationManager configManager = new FieldConfigurationManager();
        builder = new ISO8583Builder(configManager);
    }

    // ==========================================
    // SECTION 1: THE HAPPY PATH
    // ==========================================

    @Test
    void testBuild_FullStandardMessage_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "4000000000000000"); // 16 chars (LLVAR max 19)
        payload.put("3", "000000");           // 6 chars FIXED
        payload.put("4", "000000001000");     // 12 chars FIXED

        // Corrected expected string with the proper number of trailing zeroes
        String expected = "02007000000000000000164000000000000000000000000000001000";
        assertEquals(expected, builder.buildRawString(payload));
    }

    @Test
    void testBuild_OnlyMTI_ReturnsZeroBitmap() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0800");

        String expected = "08000000000000000000";
        assertEquals(expected, builder.buildRawString(payload));
    }

    @Test
    void testBuild_MissingMTI_DefaultsTo0000() {
        JSONObject payload = new JSONObject();
        payload.put("3", "123456");

        String result = builder.buildRawString(payload);
        assertTrue(result.startsWith("0000"));
    }

    // ==========================================
    // SECTION 2: BOUNDARY & EDGE CASES
    // ==========================================

    @Test
    void testBitmapGeneration_Field64Only_SetsLowestBit() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("64", "1234567890123456");

        String result = builder.buildRawString(payload);
        String primaryBitmap = result.substring(4, 20);
        assertEquals("0000000000000001", primaryBitmap, "Field 64 should trigger the lowest bit of the primary bitmap");
    }

    @Test
    void testSecondaryBitmap_GeneratesCorrectly() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "123456");
        payload.put("111", "Some private data");

        String result = builder.buildRawString(payload);
        String primaryBitmap = result.substring(4, 20);
        assertTrue(primaryBitmap.startsWith("A"), "Primary bitmap bit 1 and bit 3 should be flipped (1010 = A)");
    }

    @Test
    void testLlvar_LengthIsProperlyZeroPadded() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "12345");

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("0512345"));
    }

    @Test
    void testLllvar_LengthIsProperlyZeroPadded() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("61", "12345678");

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("00812345678"));
    }

    @Test
    void testBuilder_IgnoresMetadataKeys() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "123456");
        payload.put("transactionId", "uuid-1234-5678");

        assertDoesNotThrow(() -> builder.buildRawString(payload));
    }

    // ==========================================
    // SECTION 3: PADDING & VALIDATION TESTS
    // ==========================================

    @Test
    void testFixedField_NumericIsZeroPadded() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("4", "1000");

        String result = builder.buildRawString(payload);
        assertTrue(result.endsWith("000000001000"));
    }

    @Test
    void testFixedField_AlphanumericIsSpacePadded() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("43", "CRED");

        String result = builder.buildRawString(payload);
        String expectedPadding = String.format("%-40s", "CRED");
        assertTrue(result.endsWith(expectedPadding));
    }

    @Test
    void testFixedField_ValueTooLong_ThrowsException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "12345678");

        assertThrows(IllegalArgumentException.class, () -> builder.buildRawString(payload));
    }

    @Test
    void testLlvarField_ValueExceedsMaxLength_ThrowsException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "1234567890123456789012345");

        assertThrows(IllegalArgumentException.class, () -> builder.buildRawString(payload));
    }

    @Test
    void testLllvarField_ValueExceedsMaxLength_ThrowsException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        String massiveString = "A".repeat(1001);
        payload.put("61", massiveString);

        assertThrows(IllegalArgumentException.class, () -> builder.buildRawString(payload));
    }

    @Test
    void testMissingConfig_ThrowsException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("99", "123");

        assertThrows(IllegalStateException.class, () -> builder.buildRawString(payload));
    }

    @Test
    void testNullFieldValues_ThrowsIllegalArgumentException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", JSONObject.NULL);

        assertThrows(IllegalArgumentException.class, () -> builder.buildRawString(payload));
    }
}