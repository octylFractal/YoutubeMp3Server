package conversion;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import play.libs.EventSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;

import static conversion.Locks.using;
import static conversion.Locks.usingEx;

public class EventListener implements Iterable<Optional<EventSource.Event>>, ListChangeListener<Event> {

    private static final ExecutorService PIPE_EXECUTOR = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("pipe-sse-%d").setDaemon(true).build()
    );
    private static final ScheduledExecutorService KEEP_ALIVE = Executors.newScheduledThreadPool(2,
            new ThreadFactoryBuilder().setNameFormat("keep-alive-%d").setDaemon(true).build());
    private static final EventSource.Event KEEP_ALIVE_EVENT = EventSource.Event.event("keepAlive").withName("keepAlive");

    public static EventListener attach(ObservableList<Event> events) {
        EventListener listener = new EventListener();
        KeepAlive.schedule(listener);
        // we attach weakly so that we disappear when the event stream is closed
        events.addListener(new WeakListChangeListener<>(listener));
        // TODO there is a race condition here where things may be added twice...
        // unsure how to fix right now

        // add events currently done
        listener.fireEvent(events);
        return listener;
    }

    private final List<EventSource.Event> currentEvents = new CopyOnWriteArrayList<>();
    private final Lock currentEventsFullLock = new ReentrantLock();
    private final Condition currentEventsFull = currentEventsFullLock.newCondition();
    private final List<WeakReference<Iter>> iterators = new CopyOnWriteArrayList<>();

    private EventListener() {
    }

    public InputStream createInputStream(int skipNum) throws IOException {
        PipedOutputStream outputStream = new PipedOutputStream();

        PIPE_EXECUTOR.submit(() -> {
            Iterator<byte[]> bytes = StreamSupport.stream(spliterator(), false)
                    .skip(skipNum)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(EventSource.Event::formatted)
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .iterator();
            while (bytes.hasNext()) {
                outputStream.write(bytes.next());
            }
            return null;
        });

        return new PipedInputStream(outputStream);
    }

    @Override
    public Iterator<Optional<EventSource.Event>> iterator() {
        Iter iterator = new Iter();
        iterators.add(new WeakReference<>(iterator));
        return iterator;
    }

    @Override
    public Spliterator<Optional<EventSource.Event>> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(),
                Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL);
    }

    @Override
    public void onChanged(Change<? extends Event> c) {
        while (c.next()) {
            // we only care when events are added
            if (c.wasAdded()) {
                List<Event> added = ImmutableList.copyOf(c.getAddedSubList());
                fireEvent(added);
            }
        }
    }

    private void fireEvent(List<Event> added) {
        // ID is the index of the event
        added.forEach(e -> currentEvents.add(
                e.toSseEvent(Integer.toString(currentEvents.size()))
        ));
        // after adding events, trigger notifications
        using(currentEventsFullLock, currentEventsFull::signalAll);
    }

    private final class Iter extends AbstractIterator<Optional<EventSource.Event>> {

        private volatile boolean sendAKeepAlive;
        private int index = 0;

        @Override
        protected Optional<EventSource.Event> computeNext() {
            // don't need to lock around this check since it's a COW list
            while (currentEvents.size() <= index && !sendAKeepAlive) {
                // I really wish there was a way to use read/write locks on this
                // locking here is pretty bad for throughput

                // end of queue, block until available
                try {
                    usingEx(currentEventsFullLock, (Locks.RunnableEx) currentEventsFull::await);
                } catch (Exception ignored) {
                    return endOfData();
                }
            }
            if (sendAKeepAlive) {
                sendAKeepAlive = false;
                return Optional.of(KEEP_ALIVE_EVENT);
            }
            // queue has an event!
            EventSource.Event event = currentEvents.get(index);
            index++;
            // we MUST send a follow-up to force immediate sending...
            sendAKeepAlive = true;
            return Optional.of(event);
        }

    }

    private static final class KeepAlive implements Runnable {

        public static void schedule(EventListener eventListener) {
            KeepAlive alive = new KeepAlive(new WeakReference<>(eventListener));
            KEEP_ALIVE.scheduleAtFixedRate(alive, 0, 100, TimeUnit.MILLISECONDS);
        }

        private final WeakReference<EventListener> boundEventListener;

        private KeepAlive(WeakReference<EventListener> boundEventListener) {
            this.boundEventListener = boundEventListener;
        }

        @Override
        public void run() {
            EventListener listener = boundEventListener.get();
            if (listener == null) {
                throw new RuntimeException("reference lost, stopping keep alive");
            }
            using(listener.currentEventsFullLock, () -> {
                List<WeakReference<Iter>> iterators = listener.iterators;
                BitSet removal = new BitSet();
                for (int i = 0; i < iterators.size(); i++) {
                    Iter iterator = iterators.get(i).get();
                    if (iterator == null) {
                        removal.set(i);
                        continue;
                    }
                    iterator.sendAKeepAlive = true;
                }
                for (int i = removal.nextSetBit(0); i != -1; i = removal.nextSetBit(i + 1)) {
                    iterators.remove(i);
                }
                listener.currentEventsFull.signalAll();
            });
        }
    }
}
