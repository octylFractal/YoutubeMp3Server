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

import com.google.common.base.Strings;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UniqueId {

    private static String toHex(long val) {
        return Strings.padStart(Long.toHexString(val), 16, '0');
    }

    private final String baseId;

    private volatile long activeTimestamp;
    private final Lock STA_LOCK = new ReentrantLock();
    private volatile int sameTimestampAvoidance;

    public UniqueId(String baseId) {
        this.baseId = baseId;
    }

    private long getTimestamp() {
        return System.nanoTime();
    }

    public String next() {
        long ts = getTimestamp();
        try {
            STA_LOCK.lock();
            if (ts == activeTimestamp) {
                sameTimestampAvoidance++;
            } else {
                sameTimestampAvoidance = 0;
                activeTimestamp = ts;
            }
            return baseId + toHex(ts) + toHex(sameTimestampAvoidance) + toHex(ThreadLocalRandom.current().nextLong());
        } finally {
            STA_LOCK.unlock();
        }
    }

}
