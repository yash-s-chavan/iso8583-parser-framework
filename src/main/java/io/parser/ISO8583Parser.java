package io.parser;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject; // Ensure dependency is added to pom.xml

public class ISO8583Parser {


    private static final Map<Integer, FieldDefinition> fieldConfig = new HashMap<>();

    static {

        fieldConfig.put(2, new FieldDefinition("PAN", true, 2));

        fieldConfig.put(3, new FieldDefinition("ProcCode", false, 6));
    }

    public static String parseToXmlOrJson(String rawHexMessage) {
        JSONObject resultJson = new JSONObject();
        String mti = rawHexMessage.substring(0, 4);
        resultJson.put("MTI", mti);


        String hexBitmap = rawHexMessage.substring(4, 20);
        String binaryBitmap = hexToBinary(hexBitmap);
        resultJson.put("Bitmap", hexBitmap);

        int currentIndex = 20;
        JSONObject fieldsJson = new JSONObject();


        for (int i = 1; i < binaryBitmap.length(); i++) {
            if (binaryBitmap.charAt(i) == '1') {
                int fieldNumber = i + 1;
                FieldDefinition def = fieldConfig.get(fieldNumber);

                if (def == null) continue;

                String fieldValue = "";
                if (def.isVariable) {
                    int varLength = Integer.parseInt(rawHexMessage.substring(currentIndex, currentIndex + def.lengthIndicatorBytes));
                    currentIndex += def.lengthIndicatorBytes;
                    fieldValue = rawHexMessage.substring(currentIndex, currentIndex + varLength);
                    currentIndex += varLength;
                } else {
                    fieldValue = rawHexMessage.substring(currentIndex, currentIndex + def.fixedLength);
                    currentIndex += def.fixedLength;
                }
                fieldsJson.put("DE_" + fieldNumber, fieldValue);
            }
        }

        resultJson.put("DataElements", fieldsJson);
        return resultJson.toString(2); // Pretty-printed JSON
    }

    private static String hexToBinary(String hex) {
        StringBuilder binary = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            String binString = Integer.toBinaryString(Integer.parseInt(hex.substring(i, i + 1), 16));
            while (binString.length() < 4) {
                binString = "0" + binString; // Pad to ensure full nibble
            }
            binary.append(binString);
        }
        return binary.toString();
    }

    private static class FieldDefinition {
        String name;
        boolean isVariable;
        int fixedLength;
        int lengthIndicatorBytes;

        public FieldDefinition(String name, int fixedLength) {
            this.name = name;
            this.isVariable = false;
            this.fixedLength = fixedLength;
        }

        public FieldDefinition(String name, boolean isVariable, int lengthIndicatorBytes) {
            this.name = name;
            this.isVariable = isVariable;
            this.lengthIndicatorBytes = lengthIndicatorBytes;
        }
    }
}