package com.nmssaveexplorer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

public class SaveEncoder {

    private static final int MAGIC = 0xFEEDA1E5;
    private static final int BLOCK_SIZE = 0x10000; // 64 KB blocks

    /**
     * Re-encodes a No Man’s Sky .hg file with updated JSON data.
     * Preserves the original header if present and uses fixed 64 KB padded blocks.
     */
    public static void encodeSave(File saveFile, JsonObject saveData) throws IOException {
        // 1️⃣ Read the original file (to preserve header if any)
        byte[] original = Files.readAllBytes(saveFile.toPath());
        int headerEnd = findHeaderEnd(original);

        if (headerEnd < 0) {
            throw new IOException("Invalid .hg file: could not find MAGIC header marker.");
        }

        // Handle headerless saves gracefully
        if (headerEnd == 0) {
            System.out.println("[Encoder] No header region detected (file starts with MAGIC).");
        }

        byte[] header = (headerEnd > 0)
                ? Arrays.copyOfRange(original, 0, headerEnd)
                : new byte[0];

        // Debug header info
        if (header.length > 0) {
            System.out.println("[Encoder] Header length: " + header.length);
            System.out.printf("[Encoder] Header first 4 bytes: %02X %02X %02X %02X%n",
                    header[0], header[1], header[2], header[3]);
        } else {
            System.out.println("[Encoder] Header length: 0 (none)");
        }

        // 2️⃣ Serialize JSON compactly (no pretty-printing)
        String json = new Gson().toJson(saveData);
        byte[] input = json.getBytes(StandardCharsets.UTF_8);
        System.out.println("[Encoder] JSON byte length: " + input.length);

        // 3️⃣ Initialize LZ4 compressor
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4Compressor compressor = factory.fastCompressor();

        // 4️⃣ Write header + compressed blocks
        try (BufferedOutputStream bos =
                     new BufferedOutputStream(new FileOutputStream(saveFile))) {

            if (header.length > 0)
                bos.write(header); // preserve header if present

            int offset = 0;
            while (offset < input.length) {
                int chunkSize = Math.min(BLOCK_SIZE, input.length - offset);

                // Create a fixed 64 KB buffer (zero-padded)
                byte[] block = new byte[BLOCK_SIZE];
                System.arraycopy(input, offset, block, 0, chunkSize);

                byte[] compressed = new byte[compressor.maxCompressedLength(BLOCK_SIZE)];
                int clen = compressor.compress(block, 0, BLOCK_SIZE, compressed, 0);

                bos.write(intToBytesLE(MAGIC));      // FE ED A1 E5
                bos.write(intToBytesLE(clen));       // compressed size
                bos.write(intToBytesLE(BLOCK_SIZE)); // uncompressed size (always 0x10000)
                bos.write(intToBytesLE(0));          // reserved / padding
                bos.write(compressed, 0, clen);

                offset += chunkSize;
            }

            // EOF sentinel — exactly 16 bytes
            bos.write(intToBytesLE(MAGIC));
            bos.write(new byte[12]);
            bos.flush();
        }

        // 5️⃣ Debug final stats
        System.out.println("[Encoder] Final file size: " + saveFile.length());
        System.out.println("[Encoder] Finished encoding: " + saveFile.getName());
    }

    /** Convert int → 4-byte little-endian array. */
    private static byte[] intToBytesLE(int v) {
        return new byte[]{
                (byte) (v),
                (byte) (v >> 8),
                (byte) (v >> 16),
                (byte) (v >> 24)
        };
    }

    /**
     * Finds where the header ends (start of first data block).
     * Returns 0 for headerless saves.
     */
    private static int findHeaderEnd(byte[] data) {
        for (int i = 0; i + 4 <= data.length; i++) {
            int val = (data[i] & 0xFF)
                    | ((data[i + 1] & 0xFF) << 8)
                    | ((data[i + 2] & 0xFF) << 16)
                    | ((data[i + 3] & 0xFF) << 24);
            if (val == MAGIC) {
                // If file begins with MAGIC, headerless
                return (i == 0) ? 0 : i;
            }
        }
        return 0; // safe fallback
    }
}
