package com.nmssaveexplorer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

public class SaveDecoder {

    private static final int MAGIC = 0xFEEDA1E5;

    /**
     * Fully decompresses a No Man's Sky .hg save file into readable JSON text.
     * Handles chunked LZ4 frames and strips null padding.
     */
    public static String decodeSave(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = fis.readAllBytes();
            ByteArrayInputStream input = new ByteArrayInputStream(data);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] intBuffer = new byte[4];
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4SafeDecompressor decompressor = factory.safeDecompressor();

            while (input.available() > 0) {
                // read magic
                if (input.read(intBuffer) != 4) break;
                int magic = byteArrayToIntLE(intBuffer);
                if (magic != MAGIC) {
                    // some .hg files end with padding
                    break;
                }

                // read compressed size
                if (input.read(intBuffer) != 4) break;
                int compressedSize = byteArrayToIntLE(intBuffer);

                // read uncompressed size
                if (input.read(intBuffer) != 4) break;
                int uncompressedSize = byteArrayToIntLE(intBuffer);

                // skip unknown 4 bytes
                input.skipNBytes(4);

                // read compressed block
                byte[] compressedBlock = new byte[compressedSize];
                if (input.read(compressedBlock) != compressedSize) break;

                // decompress
                byte[] decompressedBlock = new byte[uncompressedSize];
                decompressor.decompress(compressedBlock, 0, compressedSize, decompressedBlock, 0, uncompressedSize);
                output.write(decompressedBlock);
            }

            // clean nulls and truncate broken JSON tail
            String json = output.toString(StandardCharsets.UTF_8);
            json = json.replace("\u0000", "");
            int lastGood = Math.max(json.lastIndexOf('}'), json.lastIndexOf(']'));
            if (lastGood != -1) {
                json = json.substring(0, lastGood + 1);
            }

            return json;
        }
    }

    private static int byteArrayToIntLE(byte[] bytes) {
        return ((bytes[3] & 0xFF) << 24)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[1] & 0xFF) << 8)
                | (bytes[0] & 0xFF);
    }
}
