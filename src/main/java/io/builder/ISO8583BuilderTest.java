package io.builder;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ISO8583BuilderTest {

    @Test
    public void testBasicMessageBuilding() {
        // --- APPROACH 1: Programmatic Creation ---
        // This is the cleanest way to test specific, isolated edge cases.
        JSONObject testPayload = new JSONObject();
        testPayload.put("0", "0100");
        testPayload.put("2", "1234567890123456"); // PAN (Assume LLVAR)
        testPayload.put("3", "000000");           // Proc Code (Assume Fixed 6)

        /* // --- APPROACH 2: String Parsing ---
        // Uncomment this if you want to paste in massive payloads like Sample 3.
        String rawJsonString = "{ \"0\": \"0100\", \"2\": \"1234567890123456\", \"3\": \"000000\" }";
        JSONObject testPayload = new JSONObject(rawJsonString);
        */

        // 1. Execute your custom code
        String actualRawString = ISO8583Builder.buildRawString(testPayload);

        // 2. Define the exact string we mapped out on paper earlier
        // MTI: 0100
        // Bitmap: 6000000000000000
        // Field 2 (16 chars + '16' length prefix): 161234567890123456
        // Field 3 (6 chars, fixed): 000000
        String expectedRawString = "01006000000000000000161234567890123456000000";

        // 3. Print out the results for visual debugging
        System.out.println("Expected: " + expectedRawString);
        System.out.println("Actual:   " + actualRawString);

        // 4. Run the assertion (This makes the test pass or fail in IntelliJ)
        assertEquals(expectedRawString, actualRawString, "The built ISO string did not match the expected raw output.");
    }
}