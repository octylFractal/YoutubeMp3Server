package conversion;

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
