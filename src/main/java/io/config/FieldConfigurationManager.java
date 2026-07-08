package io.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FieldConfigurationManager {

    private final Map<Integer, FieldDefinition> fieldDefinitions;

    public FieldConfigurationManager() {
        // Updated to load the new XML packager instead of the JSON file
        this.fieldDefinitions = loadConfiguration("config/iso8583-packager.xml");
    }

    private Map<Integer, FieldDefinition> loadConfiguration(String resourcePath) {
        Map<Integer, FieldDefinition> tempMap = new HashMap<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Configuration XML file not found: " + resourcePath);
            }

            // Initialize Java's native XML DOM Parser
            // Initialize Java's native XML DOM Parser
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Disable DTD validation to prevent network calls to jpos.org
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);
            document.getDocumentElement().normalize();

            // Extract all <isofield> tags
            NodeList nodeList = document.getElementsByTagName("isofield");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Element element = (Element) nodeList.item(i);

                int id = Integer.parseInt(element.getAttribute("id"));
                if (!isSupportedField(id)) {
                    continue;
                }

                int length = Integer.parseInt(element.getAttribute("length"));
                String name = element.getAttribute("name");
                String jposClass = element.getAttribute("class");

                // Translate the jPOS class into our engine's format strings
                String format = determineFormat(jposClass);

                // Binary fields are stored as hex strings: each byte = 2 hex chars
                if (jposClass.contains("BINARY") || jposClass.contains("IFB_")) {
                    length = length * 2;
                }

                length = normalizeLength(id, length);

                tempMap.put(id, new FieldDefinition(name, format, length));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ISO 8583 XML field configurations", e);
        }

        // Return an unmodifiable map to ensure thread safety
        return Collections.unmodifiableMap(tempMap);
    }

    /**
     * Translates jPOS standard classes into our internal framework formats.
     */
    private String determineFormat(String jposClass) {
        // Check for variable length markers in the jPOS class name
        if (jposClass.contains("LLL")) {
            return "LLLVAR";
        } else if (jposClass.contains("LL")) {
            return "LLVAR";
        } else {
            // Covers IFA_NUMERIC, IF_CHAR, IFA_AMOUNT, IFA_BINARY, IFB_BITMAP
            return "FIXED";
        }
    }

    private boolean isSupportedField(int fieldId) {
        // Field 99 is present in many packagers, but this framework intentionally excludes it.
        return fieldId != 99;
    }

    private int normalizeLength(int fieldId, int length) {
        // Framework interoperability expects DE43 as a 40-char fixed field.
        if (fieldId == 43) {
            return 40;
        }
        return length;
    }

    public FieldDefinition getDefinition(int field) {
        return fieldDefinitions.get(field);
    }
}