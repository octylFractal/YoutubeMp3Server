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

package net.octyl.ytmp3.conversion;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.octyl.ytmp3.util.DiskMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class ConversionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionManager.class);

    private static final UniqueId ID = new UniqueId("video");
    private static final ExecutorService CONVERSION_POOL = Executors.newFixedThreadPool(4,
        new ThreadFactoryBuilder().setNameFormat("conversion-%d").setDaemon(true).build());

    static {
        try {
            Path dbs = Paths.get("dbs");
            if (!Files.exists(dbs)) {
                Files.createDirectory(dbs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final DiskMap<Conversion> CONVERSION_MAP;
    private static final DiskMap<Conversion> RESUBMIT_MAP;

    static {
        // allow reflection to fields
        ObjectMapper JSON = new ObjectMapper();
        JSON.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        JSON.registerModule(new Jdk8Module());

        JavaType VALUE_TYPE = JSON.constructType(Conversion.class);

        CONVERSION_MAP = new DiskMap<>(JSON, VALUE_TYPE, new HashMap<>(), Paths.get("dbs/conversion-map.db"));
        RESUBMIT_MAP = new DiskMap<>(JSON, VALUE_TYPE, new HashMap<>(), Paths.get("dbs/resubmit-map.db"));
    }

    private static final Lock CONVERSION_START_LOCK = new ReentrantLock();

    public static Conversion newConversion(String video) {
        String id = ID.next();
        Conversion conversion = new Conversion(id, video);

        try {
            CONVERSION_START_LOCK.lock();

            // ensure that the conversion isn't already happening
            Conversion activeConversion = RESUBMIT_MAP.get(conversion.getStoreName());
            if (activeConversion != null) {
                if (tryReuseConversion(conversion, activeConversion)) {
                    return activeConversion;
                }
            }

            CONVERSION_POOL.submit(conversion);
            refresh(conversion);
        } finally {
            CONVERSION_START_LOCK.unlock();
        }

        return conversion;
    }

    private static boolean tryReuseConversion(Conversion conversion, Conversion activeConversion) {
        Conversion latestConversion = getConversion(activeConversion.getId());
        boolean notFailed = latestConversion == null || latestConversion.getStatus() != Status.FAILED;
        if (latestConversion != null && notFailed) {
            // re-use if not failed
            return true;
        }
        // delete the file
        try {
            Path resultFile = activeConversion.getResultFile();
            if (resultFile != null) {
                Files.deleteIfExists(resultFile);
            }
        } catch (IOException ignored) {
        }
        if (notFailed) {
            // warn on disconnects between the two maps
            LOGGER.warn("Tried to use cached conversion for " + conversion.getStoreName() + " with ID " + activeConversion.getId()
                + " but the ID didn't exist in conversion map!");
        }
        return false;
    }

    @Nullable
    public static Conversion getConversion(String id) {
        return CONVERSION_MAP.get(id);
    }

    public static void deleteConversion(String id) {
        Conversion conversion = CONVERSION_MAP.remove(id);
        if (conversion != null) {
            RESUBMIT_MAP.remove(conversion.getStoreName());
            Conversion.remove(conversion.getStoreName());
        }
    }

    public static void refresh(Conversion conversion) {
        CONVERSION_MAP.put(conversion.getId(), conversion);
        // we can also store the video ID tag for checking re-submission
        RESUBMIT_MAP.put(conversion.getStoreName(), conversion);
    }

    public static Stream<Conversion> conversions() {
        return CONVERSION_MAP.snapshot().values().stream();
    }

}
