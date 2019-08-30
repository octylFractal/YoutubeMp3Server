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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.techshroom.lettar.addons.sse.ServerSentEvent;
import com.techshroom.ytmp3.conversion.videoid.VideoId;
import com.techshroom.ytmp3.conversion.videoid.VideoIdFinder;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

public class Conversion implements Runnable {

    private static final Path DEST_DIR = Paths.get("converted");
    private static final Path WORKING_DIR;

    static {
        try {
            WORKING_DIR = Files.createTempDirectory("yt-mp3-working-dir");

            if (!Files.exists(DEST_DIR)) {
                Files.createDirectory(DEST_DIR);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final DB VIDEO_ID_RECORDS = DBMaker
        .fileDB("dbs/id-records.db")
        .closeOnJvmShutdown()
        .fileMmapEnableIfSupported()
        .make();
    private static final HTreeMap<String, String> VIDEO_ID_MAP =
        VIDEO_ID_RECORDS
            .hashMap("records")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen();

    private static final String YOUTUBE_DL = ProcessManager.resolveProgram("youtube-dl")
        .orElseThrow(() -> new IllegalStateException("Missing youtube-dl!")).toAbsolutePath().toString();

    private static final Pattern UNSAFE_FILE_NAME = Pattern.compile("[/\\\\?%*:|\"<>]");

    // Stores the entire event stream so it can be replayed from any point
    private transient final ObservableList<ServerSentEvent> events = FXCollections.observableList(new CopyOnWriteArrayList<>());
    // new events may not be fired until we start running
    private transient boolean canFireEvents;

    private void pushEvent(String type, String message) {
        if (!canFireEvents) {
            return;
        }
        events.add(ServerSentEvent.of(type, String.valueOf(events.size()), message));
        // on event, re-insert to map to freshen serialization
        ConversionManager.refresh(this);
    }

    private final String id;
    private final String video;
    private transient final Path workingDir;
    private transient final VideoId videoId;
    private transient final String storeName;
    private transient final ObjectProperty<Status> statusProperty =
        new SimpleObjectProperty<>(this, "status", Status.CREATED);
    @Nullable
    private String failureReason;
    @Nullable
    private String process;
    @Nullable
    private String rawOutput;
    @Nullable
    private String fileName;

    {
        statusProperty.addListener(observable -> {
            // fire event with new status
            pushEvent("status", getStatus().name());
        });
    }

    @JsonCreator
    public Conversion(@JsonProperty("id") String id, @JsonProperty("video") String video) {
        this.id = checkNotNull(id, "id");
        this.video = checkNotNull(video, "video");

        try {
            workingDir = WORKING_DIR.resolve(id);
            videoId = VideoIdFinder.findId(video).orElse(new VideoId("unknown", video));
            storeName = UNSAFE_FILE_NAME.matcher(videoId.getProvider() + "-" + videoId.getId())
                .replaceAll("_");
        } catch (Exception e) {
            // setup failure if it occurs early
            canFireEvents = true;
            fail(e);
            canFireEvents = false;
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    public String getId() {
        return id;
    }

    public String getVideo() {
        return video;
    }

    public Status getStatus() {
        return statusProperty.get();
    }

    public void setStatus(Status status) {
        this.statusProperty.set(status);
    }

    @Nullable
    public String getFailureReason() {
        return failureReason;
    }

    @Nullable
    public String getProcess() {
        return process;
    }

    @Nullable
    @JsonIgnore
    public Process getProcessHandle() {
        return Optional.ofNullable(getProcess()).map(ProcessManager::getProcess).orElse(null);
    }

    public String getStoreName() {
        return storeName;
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @Nullable
    private transient FileTime cachedFileTime;

    @JsonIgnore
    public synchronized FileTime getEndTime() {
        if (cachedFileTime != null) {
            return cachedFileTime;
        }
        Path resultFile = getResultFile();
        if (resultFile == null) {
            return FileTime.fromMillis(0L);
        }
        try {
            return cachedFileTime = Files.readAttributes(resultFile, BasicFileAttributes.class).lastModifiedTime();
        } catch (IOException e) {
            return FileTime.fromMillis(0L);
        }
    }

    @JsonIgnore
    @Nullable
    public Path getResultFile() {
        Path potential = DEST_DIR.resolve(storeName);
        if (Files.exists(potential)) {
            return potential;
        }
        return null;
    }

    @Nullable
    private Path getResultFileInWorkingDir() {
        try (Stream<Path> stream = Files.list(workingDir)) {
            return stream.findFirst().orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    public String getRawOutput() {
        return rawOutput;
    }

    // Don't try to serialize the observable, but do keep the event stream
    public List<JsonSSEvent> getEvents() {
        return events.stream().map(JsonSSEvent::from).collect(toImmutableList());
    }

    public void setEvents(List<JsonSSEvent> events) {
        this.events.setAll(Lists.transform(events, JsonSSEvent::toLettarEvent));
    }

    @JsonIgnore
    public ObservableList<ServerSentEvent> getObservableEvents() {
        return events;
    }

    private void fail(Exception e) {
        fail("Error: " + Throwables.getStackTraceAsString(e));
    }

    private void fail(String reason) {
        setStatus(Status.FAILED);
        failureReason = reason;
    }

    @Override
    public void run() {
        canFireEvents = true;
        if (VIDEO_ID_MAP.containsKey(storeName)) {
            fileName = VIDEO_ID_MAP.get(storeName);

            setStatus(Status.SUCCESSFUL);
            return;
        }
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        try {
            process = ProcessManager.startProcess(this::newProcess, new EventOutputStream(cap));
            setStatus(Status.CONVERTING);
            // wait for process
            Process process = getProcessHandle();
            checkNotNull(process, "process disappeared");
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                Path resultFile = getResultFileInWorkingDir();
                checkNotNull(resultFile, "no result given");

                fileName = stripId(resultFile.getFileName().toString());

                Files.move(resultFile, DEST_DIR.resolve(storeName));

                VIDEO_ID_MAP.put(storeName, fileName);
                VIDEO_ID_RECORDS.commit();

                setStatus(Status.SUCCESSFUL);
                return;
            }
            fail("Bad Exit Code " + exitCode);
        } catch (Exception e) {
            fail(e);
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        } finally {
            rawOutput = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cap.toByteArray())).toString();
            ConversionManager.refresh(this);
        }
    }

    private String stripId(String fileName) {
        // <video-name>-<id>.mp3
        return fileName.replace("-" + videoId.getId(), "");
    }

    private Process newProcess() {
        try {
            if (!Files.exists(workingDir)) {
                Files.createDirectories(workingDir);
            }
            return new ProcessBuilder(YOUTUBE_DL,
                "--prefer-ffmpeg",
                "--no-mtime",
                "--extract-audio",
                "--audio-format", "mp3",
                "--add-metadata",
                "--embed-thumbnail",
                "--output", "%(title)s.%(ext)s", video)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "Conversion[id=" + id + ",video=" + video + "]";
    }

    private class EventOutputStream extends ListeningOutputStream {

        private final ByteArrayOutputStream newlineCapture = new ByteArrayOutputStream();

        public EventOutputStream(ByteArrayOutputStream cap) {
            super(cap);
        }

        @Override
        protected void onByte(int b) {
            if (b == '\n' || b == '\r') {
                // new line, new event
                pushLine();
                if (b == '\r') {
                    pushCarriageReturn();
                }
                return;
            }
            newlineCapture.write(b);
        }

        @Override
        public void close() throws IOException {
            super.close();
            // write remaining if any
            if (newlineCapture.size() > 0) {
                pushLine();
            }
        }

        private void pushCarriageReturn() {
            pushEvent("carriageReturn", "");
        }

        private void pushLine() {
            pushEvent("outputLine", StandardCharsets.UTF_8.decode(ByteBuffer.wrap(
                newlineCapture.toByteArray())).toString());
            newlineCapture.reset();
        }
    }
}
