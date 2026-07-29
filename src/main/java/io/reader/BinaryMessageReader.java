package io.reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a binary (.bin) ISO 8583 file and converts each message into the
 * ASCII hex-string format expected by ISO8583Parser.
 *
 * Supports two common wire formats — auto-detected per message:
 *
 *   HYBRID  (most common):  [2-byte len] [4-byte ASCII MTI] [8-byte binary bitmap] [ASCII data]
 *   FULL-BINARY (jPOS):     [2-byte len] [2-byte binary MTI] [8-byte binary bitmap] [ASCII data]
 *
 * Auto-detection rule: if the first 4 bytes after the length prefix are all
 * ASCII digit characters (0x30–0x39) the hybrid format is assumed; otherwise
 * the fully-binary format is assumed.
 *
 * Secondary bitmap: detected from bit 1 (MSB) of the primary bitmap,
 * consumed as the next 8 binary bytes and appended as 16 hex chars.
 *
 * Data elements: remaining bytes are passed through as ISO-8859-1.
 */
public class BinaryMessageReader {
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageReader.class);

    public List<String> readMessages(Path filePath) throws IOException {
        byte[] raw = Files.readAllBytes(filePath);

        List<String> messages = tryLengthPrefixedFormat(raw);
        if (!messages.isEmpty()) {
            logger.info("[BinaryMessageReader] Detected length-prefixed format. Found {} message(s).", messages.size());
            return messages;
        }

        logger.info("[BinaryMessageReader] No length prefix detected. Treating file as a single raw message.");
        messages = new ArrayList<>();
        messages.add(convertMessageBytes(raw, 0, raw.length));
        return messages;
    }

    // -------------------------------------------------------------------------

    private List<String> tryLengthPrefixedFormat(byte[] raw) {
        List<String> messages = new ArrayList<>();
        int pos = 0;

        while (pos + 2 <= raw.length) {
            int msgLen = ((raw[pos] & 0xFF) << 8) | (raw[pos + 1] & 0xFF);
            pos += 2;

            if (msgLen <= 0 || pos + msgLen > raw.length) {
                return new ArrayList<>();
            }

            messages.add(convertMessageBytes(raw, pos, pos + msgLen));
            pos += msgLen;
        }

        return (pos == raw.length) ? messages : new ArrayList<>();
    }

    /**
     * Converts one raw message segment into the string format ISO8583Parser expects:
     *   MTI (4 hex/ASCII chars) + primary bitmap (16 hex) + [secondary bitmap (16 hex)] + ASCII data
     */
    private String convertMessageBytes(byte[] raw, int start, int end) {
        if (end - start < 10) {
            throw new IllegalArgumentException(
                    "Message segment too short (" + (end - start) + " bytes). " +
                    "Need at least 10 bytes (MTI + primary bitmap).");
        }

        StringBuilder sb = new StringBuilder();
        int pos = start;

        // --- Step 1: MTI ---
        // Hybrid format: MTI is 4 ASCII digit characters (e.g. "0220")
        // Full-binary:   MTI is 2 raw bytes  (e.g. 0x02 0x00 → "0200")
        if (isAsciiMti(raw, pos, end)) {
            // Pass the 4 ASCII chars through directly
            sb.append(new String(raw, pos, 4, StandardCharsets.US_ASCII));
            pos += 4;
            logger.info("[BinaryMessageReader] MTI format: ASCII 4-byte");
        } else {
            // Convert 2 binary bytes to 4 uppercase hex chars
            sb.append(String.format("%02X%02X", raw[pos] & 0xFF, raw[pos + 1] & 0xFF));
            pos += 2;
            logger.info("[BinaryMessageReader] MTI format: Binary 2-byte");
        }

        // --- Step 2: Primary bitmap (always 8 binary bytes → 16 hex chars) ---
        if (pos + 8 > end) {
            throw new IllegalArgumentException("Message truncated before primary bitmap could be read.");
        }
        java.util.HexFormat hexFmt = java.util.HexFormat.of().withUpperCase();
        sb.append(hexFmt.formatHex(raw, pos, pos + 8));

        long primaryBitmap = 0;
        for (int i = 0; i < 8; i++) {
            primaryBitmap = (primaryBitmap << 8) | (raw[pos + i] & 0xFF);
        }
        pos += 8;

        // --- Step 3: Secondary bitmap (if bit 1 of primary is set) ---
        if ((primaryBitmap & (1L << 63)) != 0) {
            if (pos + 8 > end) {
                throw new IllegalArgumentException("Message declares secondary bitmap but is truncated.");
            }
            sb.append(hexFmt.formatHex(raw, pos, pos + 8));
            pos += 8;
        }

        // --- Step 4: Data elements — pass remaining bytes through as ISO-8859-1 ---
        sb.append(new String(raw, pos, end - pos, StandardCharsets.ISO_8859_1));

        return sb.toString();
    }

    /**
     * Returns true if the 4 bytes at {@code pos} are all ASCII digit chars (0x30–0x39),
     * indicating a hybrid-format ASCII MTI.
     */
    private boolean isAsciiMti(byte[] raw, int pos, int end) {
        if (pos + 4 > end) return false;
        for (int i = pos; i < pos + 4; i++) {
            int b = raw[i] & 0xFF;
            if (b < 0x30 || b > 0x39) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------

    /** Prints a hex dump of the file — useful for debugging unknown formats. */
    public static void hexDump(Path filePath, int maxBytes) throws IOException {
        byte[] raw = Files.readAllBytes(filePath);
        int limit = Math.min(raw.length, maxBytes);
        StringBuilder sb = new StringBuilder();
        sb.append("--- HEX DUMP: ").append(filePath.getFileName()).append(" (").append(raw.length).append(" bytes total) ---\n");
        for (int i = 0; i < limit; i += 16) {
            sb.append(String.format("%04X  ", i));
            for (int j = i; j < Math.min(i + 16, limit); j++) {
                sb.append(String.format("%02X ", raw[j] & 0xFF));
            }
            for (int j = Math.min(i + 16, limit); j < i + 16; j++) {
                sb.append("   ");
            }
            sb.append(" |");
            for (int j = i; j < Math.min(i + 16, limit); j++) {
                char c = (char) (raw[j] & 0xFF);
                sb.append(c >= 32 && c < 127 ? c : '.');
            }
            sb.append("|\n");
        }
        sb.append("---");
        logger.info("\n{}", sb.toString());
    }
}
