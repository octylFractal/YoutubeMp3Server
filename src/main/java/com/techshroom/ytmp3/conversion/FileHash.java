/*
 * This file is part of YoutubeMp3Server, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.techshroom.ytmp3.conversion;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHash {

    private static final int CHUNK_SIZE = 4096;

    public static String create(Path file) throws IOException {
        Hasher hasher = Hashing.sha256().newHasher();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            while (stream.available() != -1) {
                int read = stream.read(buffer);
                if (read == -1) {
                    break;
                }

                hasher.putBytes(buffer, 0, read);
            }
        }
        return hasher.hash().toString();
    }

}
