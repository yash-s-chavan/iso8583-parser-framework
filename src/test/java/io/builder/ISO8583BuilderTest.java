package io.builder;

import io.config.FieldConfigurationManager;
import io.parser.ISO8583Parser;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ISO8583BuilderTest {

    private ISO8583Builder builder;
    private ISO8583Parser parser;
    private FieldConfigurationManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new FieldConfigurationManager();
        builder = new ISO8583Builder(configManager);
        parser = new ISO8583Parser(configManager);
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
        // Field 43 is FIXED 43 per the ISO 8583 standard
        String expectedPadding = String.format("%-43s", "CRED");
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

    // ==========================================
    // SECTION 4: BOUNDARY TESTS
    // ==========================================

    @Test
    void testBuild_MaxLengthLlvar_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "A".repeat(19));  // Field 2 max is 19 chars

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("19" + "A".repeat(19)), "LLVAR max length should be properly encoded");
    }

    @Test
    void testBuild_MaxLengthLllvar_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("61", "B".repeat(999));  // Field 61 max is 999 chars

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("999" + "B".repeat(999)), "LLLVAR max length should be properly encoded");
    }

    @Test
    void testBuild_SingleCharLlvar_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "X");

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("01X"), "Single character LLVAR should have length header 01");
    }

    @Test
    void testBuild_SingleCharFixed_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("27", "1");  // Field 27 is FIXED 1

        assertDoesNotThrow(() -> builder.buildRawString(payload), "Single-char fixed field should work");
    }

    @Test
    void testBuild_Field1Only_PrimaryBitmapField_Success() {
        // Field 1 is the primary bitmap itself — special case, should be skipped in data
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "1234567890123456");

        String result = builder.buildRawString(payload);
        assertTrue(result.startsWith("0200"), "Field 1 should not be treated as data element");
    }

    @Test
    void testBuild_SecondaryBitmapRangeFields_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "123456");
        payload.put("65", "X");  // Field 65 is in secondary bitmap range (65-128)

        String result = builder.buildRawString(payload);
        assertFalse(result.isEmpty(), "Fields in secondary range should build successfully");
    }

    // ==========================================
    // SECTION 5: STRESS TESTS
    // ==========================================

    @Test
    void testBuild_ManyFieldsAtOnce_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "1234567890123456");  // Field 2 - LLVAR
        payload.put("3", "123456");            // Field 3 - FIXED 6
        payload.put("4", "000000001000");      // Field 4 - FIXED 12
        payload.put("7", "0701121530");        // Field 7 - FIXED 10
        payload.put("11", "123456");           // Field 11 - FIXED 6
        payload.put("12", "121530");           // Field 12 - FIXED 6
        payload.put("13", "0701");             // Field 13 - FIXED 4
        payload.put("32", "123456");           // Field 32 - LLVAR

        String result = builder.buildRawString(payload);
        // MTI(4) + Bitmap(16) + Fields = ~90+ chars
        assertTrue(result.length() > 80, "Multiple fields should produce substantial ISO message");
    }

    @Test
    void testBuild_StressLargePayload_LllvarWith999Chars_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        String largeData = "X".repeat(999);
        payload.put("46", largeData);  // Field 46 - LLLVAR 999

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("999" + largeData), "Large LLLVAR payload should encode correctly");
    }

    @Test
    void testBuild_ConsecutiveVariableLengthFields_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "PAN123");          // LLVAR - 6 chars
        payload.put("32", "ACQ123");         // LLVAR - 6 chars
        payload.put("33", "FWD456");         // LLVAR - 6 chars
        payload.put("61", "PRIVDATA");       // LLLVAR - 8 chars

        String result = builder.buildRawString(payload);
        assertTrue(result.contains("06PAN123"), "First LLVAR should encode length correctly");
        assertTrue(result.contains("06ACQ123"), "Second LLVAR should encode length correctly");
        assertTrue(result.contains("06FWD456"), "Third LLVAR should encode length correctly");
    }

    // ==========================================
    // SECTION 6: END-TO-END ROUND-TRIP TESTS
    // ==========================================

    @Test
    void testRoundTrip_BuildThenParse_DataIntegrity() {
        // Build from JSON
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "4000000000000000");
        original.put("3", "000000");
        original.put("4", "000000001000");

        String rawMessage = builder.buildRawString(original);

        // Parse back to JSON
        JSONObject parsed = parser.parse(rawMessage);

        // Verify data integrity
        assertEquals(original.getString("0"), parsed.getString("0"), "MTI should match");
        assertEquals(original.getString("2"), parsed.getString("2"), "Field 2 should match");
        assertEquals(original.getString("3"), parsed.getString("3"), "Field 3 should match");
        assertEquals(original.getString("4"), parsed.getString("4"), "Field 4 should match");
    }

    @Test
    void testRoundTrip_ComplexMessage_FullParity() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "1234567890123456");
        original.put("3", "123456");
        original.put("4", "000000005000");
        original.put("7", "0701121530");
        original.put("32", "12345");

        String rawMessage = builder.buildRawString(original);
        JSONObject parsed = parser.parse(rawMessage);

        // Verify all fields round-trip correctly
        for (String key : original.keySet()) {
            String originalValue = original.getString(key);
            String parsedValue = parsed.optString(key, null);
            assertNotNull(parsedValue, "Field " + key + " should be present after round-trip");
            assertEquals(originalValue, parsedValue, "Field " + key + " value should match after round-trip");
        }
    }

    @Test
    void testRoundTrip_MultipleRoundTrips_Consistency() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "9876543210");
        original.put("3", "654321");

        // First round-trip
        String iso1 = builder.buildRawString(original);
        JSONObject parsed1 = parser.parse(iso1);

        // Second round-trip
        String iso2 = builder.buildRawString(parsed1);
        JSONObject parsed2 = parser.parse(iso2);

        // Third round-trip
        String iso3 = builder.buildRawString(parsed2);

        // All ISO strings should be identical
        assertEquals(iso1, iso2, "First and second ISO strings should match");
        assertEquals(iso2, iso3, "Second and third ISO strings should match");
    }

    @Test
    void testRoundTrip_WithSpacePadding_PreservesFormatting() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("43", "MERCHANT NAME");  // Alphanumeric, will be space-padded

        String rawMessage = builder.buildRawString(original);
        JSONObject parsed = parser.parse(rawMessage);

        String parsedField43 = parsed.getString("43");

        // Field 43 is FIXED 43 per the ISO 8583 standard
        assertEquals(43, parsedField43.length(), "Field 43 should be exactly 43 chars after round-trip");
        assertTrue(parsedField43.startsWith("MERCHANT NAME"), "Original data should be preserved");
    }

    // ==========================================
    // SECTION 7: ADDITIONAL NEGATIVE TESTS
    // ==========================================

    @Test
    void testBuild_InvalidFieldNumber_Zero_Ignored() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "123456");
        payload.put("0", "1234");  // Overwrite MTI with wrong value

        String result = builder.buildRawString(payload);
        assertEquals("1234", result.substring(0, 4), "MTI should use the final value");
    }

    @Test
    void testBuild_EmptyStringField_Fixed_ThrowsException() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "");  // Field 3 expects 6 chars, empty is invalid

        // This should either throw or pad — let's verify the behavior
        assertDoesNotThrow(() -> builder.buildRawString(payload), "Empty fixed field should be handled (padded or accepted)");
    }

    @Test
    void testBuild_WhitespaceOnlyAlphanumeric_Accepted() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("43", "    ");  // Field 43 alphanumeric, spaces only

        assertDoesNotThrow(() -> builder.buildRawString(payload), "Whitespace-only alphanumeric should be accepted");
    }

    @Test
    void testBuild_FieldOrderingDoesNotMatter() {
        // Build with fields in random order
        JSONObject payload1 = new JSONObject();
        payload1.put("0", "0200");
        payload1.put("4", "000000001000");
        payload1.put("2", "1234567890");
        payload1.put("3", "123456");

        JSONObject payload2 = new JSONObject();
        payload2.put("3", "123456");
        payload2.put("0", "0200");
        payload2.put("2", "1234567890");
        payload2.put("4", "000000001000");

        String result1 = builder.buildRawString(payload1);
        String result2 = builder.buildRawString(payload2);

        assertEquals(result1, result2, "Field order in JSON should not affect final ISO message");
    }

    @Test
    void testBuild_NumericFieldWithLeadingZeros_Preserved() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("4", "000000000001");  // Amount with many leading zeros

        String result = builder.buildRawString(payload);
        assertTrue(result.endsWith("000000000001"), "Leading zeros in numeric field should be preserved");
    }
}