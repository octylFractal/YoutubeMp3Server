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

package net.octyl.ytmp3.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkNotNull;

public class DiskMap<V> {

    private final ObjectMapper mapper;
    private final JavaType valueType;
    private final Map<String, V> map;
    private final Path file;
    // lock for MEMORY, not disk -- note that the disk ops use the "wrong" lock
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DiskMap(ObjectMapper mapper, JavaType valueType, Map<String, V> map, Path file) {
        this.mapper = mapper;
        this.valueType = valueType;
        this.map = map;
        this.file = file;
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        read();
        write();
    }

    public void write() {
        lock.readLock().lock();
        try {
            doWrite();
        } finally {
            lock.readLock().unlock();
        }
    }

    // must hold lock
    private void doWrite() {
        try (Writer writer = Files.newBufferedWriter(file)) {
            mapper.writeValue(writer, map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void read() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(file)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                map.clear();
                readValues(mapper.readTree(reader));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void readValues(JsonNode tree) {
        if (!tree.isObject()) {
            return;
        }
        for (Iterator<Entry<String, JsonNode>> fields = tree.fields(); fields.hasNext(); ) {
            Entry<String, JsonNode> field = fields.next();
            if (field.getKey() == null) {
                continue;
            }
            V value;
            try {
                value = mapper.readValue(mapper.treeAsTokens(field.getValue()), valueType);
            } catch (IOException ex) {
                value = null;
            }
            map.put(field.getKey(), value);
        }
    }

    public V get(String key) {
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public V put(String key, V val) {
        checkNotNull(key, "key");
        lock.writeLock().lock();
        try {
            V put = map.put(key, val);
            doWrite();
            return put;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V remove(String key) {
        checkNotNull(key, "key");
        lock.writeLock().lock();
        try {
            V removed = map.remove(key);
            doWrite();
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, V> snapshot() {
        lock.readLock().lock();
        try {
            return ImmutableMap.copyOf(map);
        } finally {
            lock.readLock().unlock();
        }
    }

}
