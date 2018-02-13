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

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class ProcessManager {

    private static final UniqueId ID = new UniqueId("process");
    private static final Map<String, Process> RUNNING_PROCESSES = new ConcurrentHashMap<>();

    private static final ExecutorService outputTransferrer = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("process-output-pipe-%d").setDaemon(true).build());

    static {
        new ProcessMapReaper().start();
    }

    private static final List<String> PATHEXT;
    private static final List<Path> PATH;

    static {
        Splitter PATH_SPLITTER = Splitter.on(File.pathSeparatorChar).omitEmptyStrings();
        ImmutableList.Builder<String> b = ImmutableList.builder();
        // No extension is one option
        b.add("");
        // Extensions from PATHEXT
        String pathExtEnv = System.getenv().getOrDefault("PATHEXT", "");
        b.addAll(PATH_SPLITTER.split(pathExtEnv));
        PATHEXT = b.build();
        PATH = StreamSupport.stream(PATH_SPLITTER.split(System.getenv("PATH")).spliterator(), false)
                .map(Paths::get)
                .filter(Files::exists)
                .collect(toImmutableList());
    }

    /**
     * Finds {@code program} by searching the PATH. It also adds suffixes from
     * PATHEXT.
     *
     * @param program
     *            the program to find on the PATH
     * @return the program, if found
     */
    public static Optional<Path> resolveProgram(String program) {
        return PATH.stream().flatMap(pathPart -> PATHEXT.stream().map(ext -> pathPart.resolve(program + ext))).filter(Files::exists).findFirst();
    }

    public static String startProcess(Supplier<Process> constructor, OutputStream outputAcceptor) {
        Process p = constructor.get();
        String id = ID.next();
        RUNNING_PROCESSES.put(id, p);

        // Begin cross-writing
        outputTransferrer.submit(() -> {
            try (OutputStream tmp = outputAcceptor) {
                ByteStreams.copy(p.getInputStream(), tmp);
            }
            return null;
        });

        return id;
    }

    @Nullable
    public static Process getProcess(String id) {
        return RUNNING_PROCESSES.get(id);
    }

    private static final class ProcessMapReaper extends Thread {

        private ProcessMapReaper() {
            super("ProcessMapReaper");
            setDaemon(true);
            setPriority(MIN_PRIORITY);
        }

        @Override
        public void run() {
            while (true) {
                RUNNING_PROCESSES.values().removeIf(process -> !process.isAlive());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
