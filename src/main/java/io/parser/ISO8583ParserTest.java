package io.parser;

import io.config.FieldConfigurationManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ISO8583ParserTest {

    private ISO8583Parser parser;

    @BeforeEach
    void setUp() {
        FieldConfigurationManager configManager = new FieldConfigurationManager();
        parser = new ISO8583Parser(configManager);
    }

    // ==========================================
    // SECTION 1: CORE PARSING & BITMAPS
    // ==========================================

    @Test
    void testParse_FullStandardMessage_Success() {
        // The CORRECTED 56-character string from our builder!
        String rawMessage = "02007000000000000000164000000000000000000000000000001000";

        JSONObject result = parser.parse(rawMessage);

        assertEquals("0200", result.getString("0"), "MTI should be extracted");
        assertEquals("4000000000000000", result.getString("2"), "LLVAR Field 2 should be extracted");
        assertEquals("000000", result.getString("3"), "FIXED Field 3 should be extracted");
        assertEquals("000000001000", result.getString("4"), "FIXED Field 4 should be extracted");
    }

    @Test
    void testParse_SecondaryBitmap_Success() {
        // MTI: 0200
        // Primary Bitmap: A000000000000000 (Bit 1 for Secondary, Bit 3 for Field 3)
        // Secondary Bitmap: 0000000000000000 (We'll use an empty one for this simple test)
        // Field 3 Data: 123456
        String rawMessage = "0200A0000000000000000000000000000000123456";

        JSONObject result = parser.parse(rawMessage);

        assertEquals("0200", result.getString("0"));
        assertEquals("123456", result.getString("3"), "Field 3 should be extracted after skipping the secondary bitmap");
        assertFalse(result.has("1"), "Field 1 (Secondary Bitmap) should not be exposed as a data element");
    }

    // ==========================================
    // SECTION 2: PADDING & DATA INTEGRITY
    // ==========================================

    @Test
    void testParse_FixedField_RetainsZeroPadding() {
        // Field 4 (Amount) is FIXED 12.
        // Raw string has 8 leading zeroes followed by 1000.
        String rawMessage = "02001000000000000000000000001000";

        JSONObject result = parser.parse(rawMessage);

        String field4 = result.getString("4");
        assertEquals("000000001000", field4, "Numeric leading zeroes must be preserved for exact string matching");
        assertEquals(12, field4.length(), "Extracted length must exactly match dictionary length");
    }

    @Test
    void testParse_FixedField_RetainsSpacePadding() {
        // We need a custom primary bitmap to trigger Field 43 (Card Acceptor Name)
        // Field 43 is FIXED 40. "CRED" padded with 36 spaces.
        // Bit 43 = 0000000000000000000000000000000000000000001000000000000000000000 -> Hex: 0000000000200000
        String paddedName = String.format("%-40s", "CRED");
        String rawMessage = "02000000000000200000" + paddedName;

        JSONObject result = parser.parse(rawMessage);

        String field43 = result.getString("43");
        assertEquals(paddedName, field43, "Alphanumeric trailing spaces must be preserved");
        assertEquals(40, field43.length(), "Extracted length must be exactly 40 characters");
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

        JSONObject result = parser.parse(rawMessage);

        assertEquals("123456", result.getString("32"), "LLVAR should read header and extract exact character count");
    }

    @Test
    void testParse_Lllvar_ExtractsCorrectLength() {
        // Field 61 is LLLVAR max 999.
        // Primary bitmap for bit 61: 0000000000000008
        // Data: "010" (Length header) + "1234567890" (Actual value)
        String rawMessage = "020000000000000000080101234567890";

        JSONObject result = parser.parse(rawMessage);

        assertEquals("1234567890", result.getString("61"), "LLLVAR should read 3-digit header and extract exact character count");
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
}