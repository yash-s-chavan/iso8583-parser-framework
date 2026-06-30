package io.parser;

public class Main {
    public static void main(String[] args) {
        String rawHexMessage = "01004000000000000000161234567890123456";

        System.out.println("Processing ISO8583 Message Proof of Concept...\n");
        System.out.println("Raw Input: " + rawHexMessage + "\n");

        String jsonOutput = ISO8583Parser.parseToXmlOrJson(rawHexMessage);

        System.out.println("Parsed JSON Result:");
        System.out.println(jsonOutput);
    }
}