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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.techshroom.lettar.Response;
import com.techshroom.lettar.SimpleResponse;
import com.techshroom.lettar.addons.sse.BaseSseEmitter;
import com.techshroom.lettar.addons.sse.ServerSentEvent;
import com.techshroom.lettar.addons.sse.SseEmitter;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventListStreamer {

    public static CompletionStage<? extends Response<? extends Object>> subscribe(ObservableList<ServerSentEvent> events, int skip) {
        EventListStreamer streamer = new EventListStreamer(events, skip);
        streamer.start();
        return streamer.emitter.getResponseStage();
    }

    private static final ScheduledExecutorService KEEP_ALIVE = Executors.newScheduledThreadPool(2,
        new ThreadFactoryBuilder().setNameFormat("keep-alive-%d").setDaemon(true).build());
    private static final ServerSentEvent KEEP_ALIVE_EVENT = ServerSentEvent.builder().comment("keep-alive").build();

    private final SseEmitter emitter = new BaseSseEmitter(SimpleResponse::builder);
    private final Set<String> sentEvents = new HashSet<>();
    private final Lock lock = new ReentrantLock();
    private final ObservableList<ServerSentEvent> events;
    private final int skip;

    private EventListStreamer(ObservableList<ServerSentEvent> events, int skip) {
        this.events = events;
        this.skip = skip;
    }

    private void start() {
        KEEP_ALIVE.scheduleWithFixedDelay(this::postKeepAlive, 5, 5, TimeUnit.SECONDS);

        lock.lock();
        try {
            events.addListener((ListChangeListener.Change<? extends ServerSentEvent> change) -> {
                lock.lock();
                try {
                    while (change.next()) {
                        change.getAddedSubList().forEach(this::sendEvent);
                    }
                } finally {
                    lock.unlock();
                }
            });
            events.stream()
                .skip(skip)
                .forEach(this::sendEvent);
        } finally {
            lock.unlock();
        }
    }

    private void sendEvent(ServerSentEvent event) {
        if (event.getId().isPresent() && sentEvents.contains(event.getId().get())) {
            return;
        }
        emitter.emit(event);
        event.getId().ifPresent(sentEvents::add);
    }

    private void postKeepAlive() {
        emitter.emit(KEEP_ALIVE_EVENT);
    }

}
