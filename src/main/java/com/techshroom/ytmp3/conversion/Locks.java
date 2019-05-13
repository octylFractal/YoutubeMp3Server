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

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

public class Locks {

    public static void using(Lock lock, Runnable block) {
        lock.lock();
        try {
            block.run();
        } finally {
            lock.unlock();
        }
    }

    public static void usingEx(Lock lock, RunnableEx block) throws Exception {
        lock.lock();
        try {
            block.run();
        } finally {
            lock.unlock();
        }
    }

    public static <V> V using(Lock lock, Supplier<V> block) {
        lock.lock();
        try {
            return block.get();
        } finally {
            lock.unlock();
        }
    }

    public static <V> V usingEx(Lock lock, Callable<V> block) throws Exception {
        lock.lock();
        try {
            return block.call();
        } finally {
            lock.unlock();
        }
    }

    public interface RunnableEx {

        void run() throws Exception;

    }

}
