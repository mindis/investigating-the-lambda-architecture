package ch.uzh.ddis.thesis.lambda_architecture.data.timewindow;

import ch.uzh.ddis.thesis.lambda_architecture.data.Timestamped;

/**
 * Keeps track of a sliding window
 * The window is defined with the following range [start, end)
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class SlidingWindow<E extends Timestamped> implements TimeWindow<E>{
    private final long sizeMs;
    private final long rangeMs;

    private long currentWindowStart = 0;
    private long currentWindowEnd = 0;

    // the start event keeps the start of the next sliding window in cache to provide offset guarantees.
    private E startEvent;
    private long startEventTimestamp;

    /**
     * Initializes a sliding window with the given size and range.
     * For example a sliding window of 10ms every 2ms would mean: sizeMs=10, rangeMs=2
     * @param sizeMs size of the sliding widow in milliseconds
     * @param rangeMs range of time window
     */
    public SlidingWindow(long sizeMs, long rangeMs){
        this.sizeMs = sizeMs;
        this.rangeMs = rangeMs;
    }

    @Override
    public void addEvent(E message) {
        if(this.startEvent == null){
            this.startEvent = message;
            this.startEventTimestamp = message.getTimestamp();
            this.resetWindow(message.getTimestamp());

            return;
        }

        long startDiff = message.getTimestamp() - this.startEventTimestamp;
        if(startDiff >= this.rangeMs){
            this.startEvent = message;
            this.startEventTimestamp = message.getTimestamp();
        }

        if(message.getTimestamp() > currentWindowEnd){
            // find out how many ranges to jump
            int diff = (int) Math.ceil((message.getTimestamp() - currentWindowEnd) / new Double(rangeMs));
            long newEnd = currentWindowEnd + (diff * rangeMs) + 1;
            resetWindow(newEnd - sizeMs);
        }
    }

    @Override
    public boolean isInWindow(E event) {
        if(event.getTimestamp() >= currentWindowStart && event.getTimestamp() <= currentWindowEnd){
            return true;
        }

        return false;
    }

    @Override
    public long getWindowStart() {
        return currentWindowStart;
    }

    @Override
    public long getWindowEnd() {
        return currentWindowEnd;
    }

    private void resetWindow(long start){
        this.currentWindowStart = start;
        this.currentWindowEnd = start + (sizeMs -1);
    }


    @Override
    public E getWindowOffsetEvent() {
        return this.startEvent;
    }


    @Override
    public void restoreWindow(long timestamp) {
        this.resetWindow(timestamp);
        this.startEventTimestamp = timestamp;
    }
}
