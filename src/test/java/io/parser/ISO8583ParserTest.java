package io.parser;

import io.builder.ISO8583Builder;
import io.config.FieldConfigurationManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ISO8583ParserTest {

    private ISO8583Parser parser;
    private ISO8583Builder builder;
    private FieldConfigurationManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new FieldConfigurationManager();
        parser = new ISO8583Parser(configManager);
        builder = new ISO8583Builder(configManager);
    }

    // ==========================================
    // SECTION 1: CORE PARSING & BITMAPS
    // ==========================================

    @Test
    void testParse_FullStandardMessage_Success() {
        // The CORRECTED 56-character string from our builder!
        String rawMessage = "02007000000000000000164000000000000000000000000000001000";

        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("0200", result.get("0"), "MTI should be extracted");
        assertEquals("4000000000000000", result.get("2"), "LLVAR Field 2 should be extracted");
        assertEquals("000000", result.get("3"), "FIXED Field 3 should be extracted");
        assertEquals("000000001000", result.get("4"), "FIXED Field 4 should be extracted");
    }

    @Test
    void testParse_SecondaryBitmap_Success() {
        // MTI: 0200
        // Primary Bitmap: A000000000000000 (Bit 1 for Secondary, Bit 3 for Field 3)
        // Secondary Bitmap: 0000000000000000 (We'll use an empty one for this simple test)
        // Field 3 Data: 123456
        String rawMessage = "0200A0000000000000000000000000000000123456";

        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("0200", result.get("0"));
        assertEquals("123456", result.get("3"), "Field 3 should be extracted after skipping the secondary bitmap");
        assertFalse(result.containsKey("1"), "Field 1 (Secondary Bitmap) should not be exposed as a data element");
    }

    // ==========================================
    // SECTION 2: PADDING & DATA INTEGRITY
    // ==========================================

    @Test
    void testParse_FixedField_RetainsZeroPadding() {
        // Field 4 (Amount) is FIXED 12.
        // Raw string has 8 leading zeroes followed by 1000.
        String rawMessage = "02001000000000000000000000001000";

        Map<String, String> result = parser.parse(rawMessage);

        String field4 = result.get("4");
        assertEquals("000000001000", field4, "Numeric leading zeroes must be preserved for exact string matching");
        assertEquals(12, field4.length(), "Extracted length must exactly match dictionary length");
    }

    @Test
    void testParse_FixedField_RetainsSpacePadding() {
        // We need a custom primary bitmap to trigger Field 43 (Card Acceptor Name)
        // Field 43 is FIXED 43 per the ISO 8583 standard. "CRED" padded with 39 spaces.
        // Bit 43 = Hex: 0000000000200000
        String paddedName = String.format("%-43s", "CRED");
        String rawMessage = "02000000000000200000" + paddedName;

        Map<String, String> result = parser.parse(rawMessage);

        String field43 = result.get("43");
        assertEquals(paddedName, field43, "Alphanumeric trailing spaces must be preserved");
        assertEquals(43, field43.length(), "Extracted length must be exactly 43 characters");
    }

    // ==========================================
    // SECTION 3: VARIABLE LENGTH FIELDS
    // ==========================================

    @Test
    void testParse_Llvar_ExtractsCorrectLength() {
        // Field 32 is LLVAR max 11.
        // Primary bitmap for bit 32: 0000000100000000
        // Data: "06" (Length header) + "123456" (Actual value)
        String rawMessage = "0200000000010000000006123456";

        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("123456", result.get("32"), "LLVAR should read header and extract exact character count");
    }

    @Test
    void testParse_Lllvar_ExtractsCorrectLength() {
        // Field 61 is LLLVAR max 999.
        // Primary bitmap for bit 61: 0000000000000008
        // Data: "010" (Length header) + "1234567890" (Actual value)
        String rawMessage = "020000000000000000080101234567890";

        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("1234567890", result.get("61"), "LLLVAR should read 3-digit header and extract exact character count");
    }

    // ==========================================
    // SECTION 4: ERROR HANDLING
    // ==========================================

    @Test
    void testParse_MessageTooShort_ThrowsException() {
        // Truncated message: Declares Field 3 (FIXED 6) but string ends early
        String rawMessage = "02002000000000000000123";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should throw an exception if the raw string ends before a field is fully extracted");
    }

    @Test
    void testParse_LlvarHeaderExceedsMaxLength_ThrowsException() {
        // Field 32 is LLVAR max 11.
        // Data: "15" (Length header - ILLEGAL) + "123456789012345"
        String rawMessage = "0200000000010000000015123456789012345";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser must validate that the incoming length header does not exceed the dictionary maximum");
    }

    // ==========================================
    // SECTION 5: BOUNDARY TESTS
    // ==========================================

    @Test
    void testParse_MaxLengthLlvar_Success() {
        // Field 2 max 19 chars
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "A".repeat(19));

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("A".repeat(19), result.get("2"), "Max-length LLVAR should parse correctly");
    }

    @Test
    void testParse_MaxLengthLllvar_Success() {
        // Field 61 max 999 chars
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("61", "B".repeat(999));

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("B".repeat(999), result.get("61"), "Max-length LLLVAR should parse correctly");
    }

    @Test
    void testParse_SingleCharLlvar_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "X");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("X", result.get("2"), "Single-character LLVAR should parse correctly");
    }

    @Test
    void testParse_MinimumLengthFixed_Success() {
        // Field 27 is FIXED 1 (minimum for most fields)
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("27", "1");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("1", result.get("27"), "Minimum-length fixed field should parse correctly");
    }

    @Test
    void testParse_SecondaryBitmapRange_Fields65To128_Success() {
        // Test a field in secondary bitmap range (65-128)
        // Field 65 is IFA_BINARY length 1 (hex = 2 chars)
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("3", "123456");
        payload.put("65", "AB");  // Binary field, 2 hex chars max

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("AB", result.get("65"), "Secondary bitmap range field should parse correctly");
    }

    // ==========================================
    // SECTION 6: STRESS TESTS
    // ==========================================

    @Test
    void testParse_ManyFieldsAtOnce_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "1234567890123456");
        payload.put("3", "123456");
        payload.put("4", "000000001000");
        payload.put("7", "0701121530");
        payload.put("11", "123456");
        payload.put("12", "121530");
        payload.put("13", "0701");
        payload.put("32", "123456");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals(9, result.size(), "All 8 data fields plus MTI should be present");
        assertEquals("0200", result.get("0"));
        assertEquals("1234567890123456", result.get("2"));
        assertEquals("123456", result.get("3"));
    }

    @Test
    void testParse_LargePayload_999CharLllvar_Success() {
        String largeData = "X".repeat(999);
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("46", largeData);

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals(largeData, result.get("46"), "Large 999-char LLLVAR should parse correctly");
    }

    @Test
    void testParse_ConsecutiveVariableLengthFields_Success() {
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "PAN123");
        payload.put("32", "ACQ123");
        payload.put("33", "FWD456");
        payload.put("61", "PRIVDATA");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("PAN123", result.get("2"), "First LLVAR should parse correctly");
        assertEquals("ACQ123", result.get("32"), "Second LLVAR should parse correctly");
        assertEquals("FWD456", result.get("33"), "Third LLVAR should parse correctly");
        assertEquals("PRIVDATA", result.get("61"), "LLLVAR should parse correctly");
    }

    // ==========================================
    // SECTION 7: END-TO-END ROUND-TRIP TESTS
    // ==========================================

    @Test
    void testRoundTrip_BuildThenParse_CompleteDataIntegrity() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "4000000000000000");
        original.put("3", "000000");
        original.put("4", "000000001000");
        original.put("7", "0701121530");
        original.put("11", "123456");

        String rawMessage = builder.buildRawString(original);
        Map<String, String> parsed = parser.parse(rawMessage);

        for (String key : original.keySet()) {
            assertEquals(original.getString(key), parsed.get(key),
                    "Field " + key + " should survive round-trip");
        }
    }

    @Test
    void testRoundTrip_MultipleConsecutiveRoundTrips_Consistency() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "9876543210");
        original.put("3", "654321");
        original.put("4", "000000002500");

        String iso1 = builder.buildRawString(original);
        Map<String, String> parsed1 = parser.parse(iso1);

        String iso2 = builder.buildRawString(parsed1);
        Map<String, String> parsed2 = parser.parse(iso2);

        String iso3 = builder.buildRawString(parsed2);

        // All ISO strings should be identical
        assertEquals(iso1, iso2, "First and second ISO should match");
        assertEquals(iso2, iso3, "Second and third ISO should match");
    }

    @Test
    void testRoundTrip_WithMixedFieldTypes_PreservesAll() {
        JSONObject original = new JSONObject();
        original.put("0", "0200");
        original.put("2", "LLVAR123");          // LLVAR
        original.put("3", "000001");            // FIXED numeric
        original.put("43", "MERCHANT");         // FIXED alphanumeric (will be space-padded)
        original.put("61", "PRIVDATA");         // LLLVAR

        String rawMessage = builder.buildRawString(original);
        Map<String, String> parsed = parser.parse(rawMessage);

        assertEquals("LLVAR123", parsed.get("2"));
        assertEquals("000001", parsed.get("3"));
        // Field 43 is space-padded to 40 chars, so compare trimmed
        assertEquals("MERCHANT", parsed.get("43").trim());
        assertEquals("PRIVDATA", parsed.get("61"));
    }

    // ==========================================
    // SECTION 8: ADDITIONAL EDGE CASES
    // ==========================================

    @Test
    void testParse_AllFieldsPresent_Success() {
        // Build a message with many fields to verify bitmap parsing
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        for (int i = 2; i <= 10; i++) {
            payload.put(String.valueOf(i), "field" + i);
        }

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals(10, result.size(), "All 9 fields (2-10) plus MTI should be present");
    }

    @Test
    void testParse_BitmapWithAlternatingBits_Success() {
        // Create a specific bitmap pattern: fields 2, 4, 6, 7 (alternating)
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "FIELD2");
        payload.put("4", "000000001000");
        payload.put("6", "000000000500");
        payload.put("7", "0701121530");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("FIELD2", result.get("2"));
        assertEquals("000000001000", result.get("4"));
        assertEquals("000000000500", result.get("6"));
        assertEquals("0701121530", result.get("7"));
    }

    @Test
    void testParse_ZeroPaddedNumericFields_Preserved() {
        // Ensure leading zeros are NOT stripped
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("4", "000000000001");
        payload.put("7", "0000000001");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        assertEquals("000000000001", result.get("4"), "Zero padding must be preserved");
        assertEquals("0000000001", result.get("7"), "Leading zeros must not be stripped");
    }

    @Test
    void testParse_SpacePaddedAlphanumericFields_Preserved() {
        // Ensure trailing spaces are NOT stripped
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("43", "SHORT");

        String rawMessage = builder.buildRawString(payload);
        Map<String, String> result = parser.parse(rawMessage);

        String field43 = result.get("43");
        assertEquals(43, field43.length(), "Space-padded field should retain full standard length of 43");
        assertTrue(field43.startsWith("SHORT"), "Original data should be at start");
        assertTrue(field43.endsWith("    "), "Padding spaces should be at end");
    }

    // ==========================================
    // SECTION 9: NEGATIVE / ERROR HANDLING
    // ==========================================

    @Test
    void testParse_InvalidBitmapHex_ThrowsException() {
        // Invalid hex characters in bitmap
        String rawMessage = "0200GGGGGGGGGGGGGGGG";  // GG is invalid hex

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject invalid hex in bitmap");
    }

    @Test
    void testParse_TruncatedBitmap_ThrowsException() {
        // Bitmap too short (should be 16 hex chars)
        String rawMessage = "020070000000";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject truncated bitmap");
    }

    @Test
    void testParse_TruncatedSecondaryBitmap_ThrowsException() {
        // Secondary bitmap declared but truncated
        String rawMessage = "0200A000000000000000";  // A = primary bit 1 set, but no secondary bitmap

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject truncated secondary bitmap");
    }

    @Test
    void testParse_FieldDeclaresLongerThanMessage_ThrowsException() {
        // Bitmap says field 3 (FIXED 6) is present, but message ends early
        String rawMessage = "02002000000000000000123";  // Only 3 chars for field 3

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject truncated field data");
    }

    @Test
    void testParse_LlvarHeaderExceedsMax_ThrowsException() {
        // Field 2 is LLVAR max 19
        // Send header "25" (25 > 19)
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("2", "A".repeat(19));

        String rawMessage = builder.buildRawString(payload);
        // Manually corrupt the length header
        String corruptedMessage = rawMessage.replaceFirst("19", "25");

        assertThrows(IllegalArgumentException.class, () -> parser.parse(corruptedMessage),
                "Parser should reject LLVAR length exceeding max");
    }

    @Test
    void testParse_LllvarHeaderExceedsMax_ThrowsException() {
        // Field 61 is LLLVAR max 999
        // Send a message with header > 999
        JSONObject payload = new JSONObject();
        payload.put("0", "0200");
        payload.put("61", "X");

        String rawMessage = builder.buildRawString(payload);
        // Manually corrupt the length header
        String corruptedMessage = rawMessage.replace("001X", "1234X");

        assertThrows(IllegalArgumentException.class, () -> parser.parse(corruptedMessage),
                "Parser should reject LLLVAR length exceeding max");
    }

    @Test
    void testParse_MessageEndsInMiddleOfLlvarHeader_ThrowsException() {
        // Bitmap says field 2 (LLVAR) is present, but message ends before we can read the 2-digit header
        String rawMessage = "02007000000000000000";  // Field 2 is present but message ends

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject message truncated during LLVAR header read");
    }

    @Test
    void testParse_EmptyMessage_ThrowsException() {
        String rawMessage = "";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject empty message");
    }

    @Test
    void testParse_MissingMTI_ThrowsException() {
        String rawMessage = "123456789";  // Not enough chars for MTI + bitmap

        assertThrows(IllegalArgumentException.class, () -> parser.parse(rawMessage),
                "Parser should reject message shorter than MTI + bitmap");
    }
}


