package io.parser;

import io.builder.ISO8583Builder;
import io.config.FieldConfigurationManager;
import org.json.JSONObject;

import java.util.Map;

public class UserPayloadTest {
    public static void main(String[] args) {
        String jsonStr = """
{
  "0": "0220",
  "2": "333001000872416",
  "3": "000000",
  "4": "000000000005",
  "5": "000000000005",
  "6": "000000000000",
  "7": "0623000000",
  "9": "61000000",
  "10": "61000000",
  "11": "063484",
  "12": "000000",
  "13": "0604",
  "15": "0623",
  "18": "5310",
  "28": "D00000000",
  "32": "444500",
  "37": "615522200312",
  "38": "DGQ1LF",
  "39": "00",
  "41": "31263484",
  "42": "4445003423665  ",
  "43": "TARGET T-2366            DAVENPORT    FLUSA",
  "49": "840",
  "50": "840",
  "51": "840",
  "54": "0001840C0000001377610002840C0000001270660003840D0000000122390004840D0000000229340040840D000000000000",
  "63": "002     346155631336928           0                       VISA        ",
  "111": " 346155631336928     00                          000000 0000000000 000000000000     0000000000            002 0013909100000000                     0DGQ1LF                                                                000000000000000                                            00000        0000000000                   0000 20"
 }
""";
        try {
            FieldConfigurationManager configManager = new FieldConfigurationManager();
            ISO8583Builder builder = new ISO8583Builder(configManager);
            ISO8583Parser parser = new ISO8583Parser(configManager);

            JSONObject original = new JSONObject(jsonStr);
            System.out.println("Building message...");
            String rawMessage = builder.buildRawString(original);
            System.out.println("Built Raw Message: " + rawMessage);

            System.out.println("Parsing message...");
            Map<String, String> parsed = parser.parse(rawMessage);
            
            boolean match = true;
            for (String key : original.keySet()) {
                String originalValue = original.getString(key);
                String parsedValue = parsed.get(key);
                if (!originalValue.equals(parsedValue)) {
                    System.out.println("Mismatch on Field " + key + ":");
                    System.out.println("  Original: '" + originalValue + "' (Length: " + originalValue.length() + ")");
                    System.out.println("  Parsed:   '" + parsedValue + "' (Length: " + (parsedValue != null ? parsedValue.length() : "null") + ")");
                    match = false;
                }
            }
            if (match) {
                System.out.println("All fields match perfectly!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
