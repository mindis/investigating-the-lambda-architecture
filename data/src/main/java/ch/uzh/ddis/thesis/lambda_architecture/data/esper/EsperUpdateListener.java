package ch.uzh.ddis.thesis.lambda_architecture.data.esper;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import org.javatuples.Pair;

/**
 * A simple Update Listener, that stores the results from the last update
 * and after polling the latest updates, it will reset. Therefore, one can
 * simply use this class to poll for new events published by esper.
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class EsperUpdateListener implements UpdateListener{

    private boolean newData;
    private EventBean[] newEventsCache;
    private EventBean[] oldEventsCache;

    public EsperUpdateListener(){
        this.newData = false;
    }


    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
        // apparently esper also sends an update if no events were matching.
        if (newEvents != null){
            this.newData = true;
            this.newEventsCache = newEvents;
            this.oldEventsCache = oldEvents;
        }
    }

    /**
     * Checks whether new results are available.
     * @return true if new results are available.
     */
    public boolean hasNewData() {
        return newData;
    }

    /**
     * get the new data from the listener.
     * @return tuple (new events, old events) for the latest update.
     */
    public Pair<EventBean[], EventBean[]> getNewData(){
        this.newData = false;
        return new Pair<>(this.newEventsCache, this.oldEventsCache);
    }
}
