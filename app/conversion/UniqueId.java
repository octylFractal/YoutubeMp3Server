package conversion;

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
