package conversion;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import javax.annotation.Nullable;
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

public class ProcessManager {

    private static final UniqueId ID = new UniqueId("process");
    private static final Map<String, Process> RUNNING_PROCESSES = new ConcurrentHashMap<>();

    private static final ExecutorService outputTransferrer = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("process-output-pipe-%d").setDaemon(true).build()
    );

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
                .collect(MoreCollectors.toImmutableList());
    }

    /**
     * Finds {@code program} by searching the PATH. It also adds suffixes from PATHEXT.
     *
     * @param program the program to find on the PATH
     * @return the program, if found
     */
    public static Optional<Path> resolveProgram(String program) {
        return PATH.stream().flatMap(pathPart ->
                PATHEXT.stream().map(ext -> pathPart.resolve(program + ext))
        ).filter(Files::exists).findFirst();
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
