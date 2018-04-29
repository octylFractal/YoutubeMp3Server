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
package com.techshroom.ytmp3.util;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

public class DiskMap<K, V> {

    private final ObjectMapper mapper;
    private final JavaType mapKind;
    private final Map<K, V> map;
    private final Path file;

    public DiskMap(ObjectMapper mapper, JavaType mapKind, Map<K, V> map, Path file) {
        this.mapper = mapper;
        this.mapKind = mapKind;
        this.map = map;
        this.file = file;
        read();
        write();
    }

    public synchronized void write() {
        try (Writer writer = Files.newBufferedWriter(file)) {
            mapper.writeValue(writer, map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void read() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                map.clear();
                map.putAll(mapper.readValue(reader, mapKind));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized V put(K key, V val) {
        V v = map.put(key, val);
        if (v != val) {
            write();
        }
        return v;
    }

    public synchronized void useMap(Consumer<Map<K, V>> cons) {
        cons.accept(map);
        write();
    }

    public synchronized void useMap(Predicate<Map<K, V>> cons) {
        if (cons.test(map)) {
            write();
        }
    }

    public synchronized Map<K, V> snapshot() {
        return ImmutableMap.copyOf(map);
    }

}
