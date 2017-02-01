package conversion;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import play.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConversionManager {

    private static final UniqueId ID = new UniqueId("video");
    private static final ExecutorService CONVERSION_POOL = Executors.newFixedThreadPool(4,
            new ThreadFactoryBuilder().setNameFormat("conversion-%d").setDaemon(true).build()
    );

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

    private static final DB CONVERSION_DB_DISK = DBMaker
            .fileDB("dbs/conversion.db")
            .closeOnJvmShutdown()
            .fileMmapEnableIfSupported()
            .fileLockDisable()
            .make();
    private static final DB CONVERSION_DB_MEM = DBMaker
            .heapDB()
            .closeOnJvmShutdown()
            .make();
    private static final Serializer<Conversion> CONVERSION_SERIALIZER = JsonSerializer.instance(Conversion.class);
    private static final HTreeMap<String, Conversion> CONVERSION_MAP_DISK =
            CONVERSION_DB_DISK.hashMap("conversion")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(CONVERSION_SERIALIZER)
                    .createOrOpen();
    private static final ObservableMap<String, Conversion> WEAK_CONVERSION_MAP;

    static {
        ObservableMap<String, Conversion> map = FXCollections.observableMap(new WeakHashMap<>());
        map.addListener((MapChangeListener<String, Conversion>) change -> {
            if (change.wasAdded()) {
                CONVERSION_MAP_DISK.put(change.getKey(), change.getValueAdded());
            } else if (change.wasRemoved()) {
                CONVERSION_MAP_DISK.remove(change.getKey());
            }
            CONVERSION_DB_DISK.commit();
        });
        WEAK_CONVERSION_MAP = map;
    }

    private static final HTreeMap<String, Conversion> CONVERSION_MAP =
            CONVERSION_DB_MEM.hashMap("conversion")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(CONVERSION_SERIALIZER)
                    .expireOverflow(WEAK_CONVERSION_MAP)
                    .expireAfterGet(1, TimeUnit.SECONDS)
                    .expireExecutor(Executors.newScheduledThreadPool(2))
                    .createOrOpen();
    private static final Lock CONVERSION_START_LOCK = new ReentrantLock();

    public static Conversion newConversion(String video) {
        String id = ID.next();
        Conversion conversion = new Conversion(id, video);

        try {
            CONVERSION_START_LOCK.lock();

            // ensure that the conversion isn't already happening
            Conversion activeConversion = CONVERSION_MAP.get(conversion.getStoreName());
            if (activeConversion != null) {
                // use get to force load from disk
                if (CONVERSION_MAP.get(activeConversion.getId()) != null) {
                    return activeConversion;
                }
                // delete the file
                try {
                    Path resultFile = activeConversion.getResultFile();
                    if (resultFile != null) {
                        Files.deleteIfExists(resultFile);
                    }
                } catch (IOException ignored) {
                }
                Logger.warn("CMAP had a conversion for " + conversion.getStoreName() + " with ID " + activeConversion.getId() + " but the ID didn't exist in CMAP!");
            }

            CONVERSION_POOL.submit(conversion);
            refresh(conversion);
        } finally {
            CONVERSION_START_LOCK.unlock();
        }

        return conversion;
    }

    @Nullable
    public static Conversion getConversion(String id) {
        return CONVERSION_MAP.get(id);
    }

    public static void refresh(Conversion conversion) {
        CONVERSION_MAP.put(conversion.getId(), conversion);
        // we can also store the video ID tag for checking re-submission
        CONVERSION_MAP.put(conversion.getStoreName(), conversion);

        // Commit memory
        CONVERSION_DB_MEM.commit();
        // Save to disk
        CONVERSION_DB_DISK.commit();
    }

}
