package conversion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import conversion.videoid.VideoId;
import conversion.videoid.VideoIdFinder;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class Conversion implements Runnable {

    private static final Path DEST_DIR = Paths.get("converted");
    private static final Path WORKING_DIR;

    static {
        try {
            WORKING_DIR = Files.createTempDirectory("yt-mp3-working-dir");

            if (!Files.exists(DEST_DIR)) {
                Files.createDirectory(DEST_DIR);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
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

    // Stores the entire event stream so it can be replayed from any point
    private transient final ObservableList<Event> events = FXCollections.observableList(new CopyOnWriteArrayList<>());
    // new events may not be fired until we start running
    private transient boolean canFireEvents;

    private void pushEvent(String type, String message) {
        if (!canFireEvents) {
            return;
        }
        events.add(new Event(type, message));
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
        this.id = id;
        this.video = video;

        try {
            workingDir = WORKING_DIR.resolve(id);
            videoId = VideoIdFinder.findId(video)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown video type " + video));
            storeName = videoId.getProvider() + "-" + videoId.getId();
        } catch (Exception e) {
            // setup failure if it occurs early
            canFireEvents = true;
            fail(e);
            canFireEvents = false;
            throw Throwables.propagate(e);
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
    public List<Event> getEvents() {
        return ImmutableList.copyOf(events);
    }

    public void setEvents(List<Event> events) {
        this.events.setAll(events);
    }

    @JsonIgnore
    public ObservableList<Event> getObservableEvents() {
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
            throw Throwables.propagate(e);
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
            return new ProcessBuilder(YOUTUBE_DL, "--extract-audio", "--audio-format", "mp3", video)
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
            if (b == '\n') {
                // new line, new event
                pushLine();
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

        private void pushLine() {
            pushEvent("outputLine", StandardCharsets.UTF_8.decode(ByteBuffer.wrap(
                    newlineCapture.toByteArray()
            )).toString());
            newlineCapture.reset();
        }
    }
}
