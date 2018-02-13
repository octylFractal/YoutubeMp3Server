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
package com.techshroom.ytmp3.controllers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;

public class AssetCacher {

    private static String assetKey(URL resource) {
        String[] parts = resource.getPath().split("\\.", 2);
        String ext = "";
        if (parts.length > 1) {
            ext = "." + parts[1];
        }
        String primaryName = Hashing.sha256().hashString(resource.toString(), StandardCharsets.UTF_8).toString();
        return primaryName + ext;
    }

    private final Path cacheDirectory;
    {
        try {
            this.cacheDirectory = Files.createTempDirectory("youtube-mp3-server");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    private final Set<String> cached = Sets.newConcurrentHashSet();
    private final Lock lock = new ReentrantLock();

    public Path getAsset(URL resource) {
        String key = assetKey(resource);
        if (!cached.contains(key)) {
            lock.lock();
            try {
                if (!cached.contains(key)) {
                    cache(key, resource);
                    cached.add(key);
                }
            } finally {
                lock.unlock();
            }
        }
        return cacheDirectory.resolve(key);
    }

    private void cache(String key, URL resource) {
        Path location = cacheDirectory.resolve(key);
        try (InputStream input = resource.openStream();
                OutputStream output = new BufferedOutputStream(Files.newOutputStream(location))) {
            ByteStreams.copy(input, output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
