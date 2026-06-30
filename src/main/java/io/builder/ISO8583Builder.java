package io.builder;

import org.json.JSONObject;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ISO8583Builder {

    /**
     * Converts a mapped JSON object back into a raw ISO8583 Hex String.
     */
    public static String buildRawString(JSONObject jsonPayload) {
        StringBuilder finalMessage = new StringBuilder();

        // 1. Get MTI (Assuming "0" or "MTI" is the key based on your samples)
        String mti = jsonPayload.optString("0", "0000");
        finalMessage.append(mti);

        // TODO: 2. Extract all Data Element keys from the JSON and sort them numerically

        long bitmap = 0;
        List<Integer> fields = new ArrayList<>();
        for(String str: jsonPayload.keySet()){
            int value = Integer.parseInt(str);
            if(value != 0) {
                fields.add(value);
                bitmap |= (1L << (64-value));
            }
        }
        Collections.sort(fields);

        // TODO: 3. Generate the 64-bit binary bitmap, then convert to Hex
        System.out.println(fields);
        String hexStr = Long.toHexString(bitmap);
        finalMessage.append(hexStr);
        // TODO: 4. Format and append each Data Element based on configuration rules
        for(int field: fields){

        }
        return finalMessage.toString();
    }
}