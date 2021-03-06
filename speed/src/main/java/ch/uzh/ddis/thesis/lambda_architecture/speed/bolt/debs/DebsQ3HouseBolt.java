package ch.uzh.ddis.thesis.lambda_architecture.speed.bolt.debs;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import ch.uzh.ddis.thesis.lambda_architecture.data.SimpleTimestamp;
import ch.uzh.ddis.thesis.lambda_architecture.data.Timestamped;
import ch.uzh.ddis.thesis.lambda_architecture.data.debs.DebsDataEntry;
import ch.uzh.ddis.thesis.lambda_architecture.data.esper.EsperFactory;
import ch.uzh.ddis.thesis.lambda_architecture.data.esper.EsperUpdateListener;
import ch.uzh.ddis.thesis.lambda_architecture.data.timewindow.TimeWindow;
import ch.uzh.ddis.thesis.lambda_architecture.data.timewindow.TumblingWindow;
import ch.uzh.ddis.thesis.lambda_architecture.data.utils.Round;
import com.ecyrd.speed4j.StopWatch;
import com.espertech.esper.client.*;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.google.common.base.Optional;
import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.javatuples.Pair;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Storm task to solve the query `average load` on the debs data set.
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class DebsQ3HouseBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger();
    private static final Marker performance = MarkerManager.getMarker("PERFORMANCE");
    private static final Marker remoteDebug = MarkerManager.getMarker("DEBUGFLUME");

    private OutputCollector outputCollector;
    private Map config;
    private TopologyContext context;

    private int taskId;

    private final long windowSizeMinutes;
    private final long windowSize;
    private final String taskName;

    private static final String esperEngineName = "debs-q3-house";
    private static final String esperQueryPath = "/esper-queries/debs-q1-house.esper";

    private EsperUpdateListener esperUpdateListener;
    private String query;
    private EPRuntime esper;

    private TimeWindow<Timestamped> timeWindow;
    private String firstTimestampKey;
    private boolean firstTimestampSaved = false;

    private Jedis redisCache;
    private String redisHost;

    private long lastTimestamp = 0;
    private long lastDataReceived;
    private long processCounter = 0;
    private StopWatch processWatch;

    public DebsQ3HouseBolt(String redisHost, long windowSizeMinutes) {
        this.redisHost = redisHost;
        this.windowSizeMinutes = windowSizeMinutes;
        this.windowSize = windowSizeMinutes * 60 * 1000;

        this.taskName = this.esperEngineName + "-" + this.windowSizeMinutes + "min";
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.outputCollector = collector;
        this.config = stormConf;
        this.context = context;
        this.taskId = context.getThisTaskIndex();
        this.firstTimestampKey = new StringBuilder().append(taskId).append("_").append("start_timestamp").toString();

        this.redisCache = new Jedis(redisHost);

        this.timeWindow = new TumblingWindow<>(windowSize);
        this.initEsper();

        this.restoreTimewindow();
    }

    @Override
    public void execute(Tuple input) {
        DebsDataEntry entry = new DebsDataEntry((String) input.getValueByField("data"));

        if(!firstTimestampSaved){
            this.redisCache.set(firstTimestampKey, String.valueOf(entry.getTimestamp()));
            firstTimestampSaved = true;

            timeWindow.addEvent(entry);
        }

        this.sendTimeEvent(entry.getTimestamp());
        this.esper.sendEvent(entry.getMap(), entry.getType().toString());

        if(!this.timeWindow.isInWindow(entry)) {
            this.processNewData();
        }

        this.timeWindow.addEvent(entry);

        this.outputCollector.ack(input);

        if(this.processCounter == 0){
            this.processWatch = new StopWatch();
        }

        this.processCounter++;
        if(this.processCounter % 1000 == 0){
            this.processWatch.stop();
            logger.info(performance, "topic={} stepSize={} duration={} timestamp={} datatimestamp={} threadId={}",
                    "boltMessageThroughput", "1000", this.processWatch.getTimeMicros(), System.currentTimeMillis(),
                    entry.getTimestamp(), this.taskId);
            this.processWatch = new StopWatch();
        }
    }

    private void processNewData(){
        if(this.esperUpdateListener.hasNewData()){
            Pair<EventBean[], EventBean[]> eventDataTouple = this.esperUpdateListener.getNewData();
            EventBean[] newEvents = eventDataTouple.getValue0();

            for(int i = 0; i < newEvents.length; i++){
                String houseId = String.valueOf(newEvents[i].get("houseId"));
                Double load = (Double) newEvents[i].get("load");

                if(load == null){
                    continue;
                }

                load = Round.roundToFiveDecimals(load);

                HashMap<String, Object> result = new HashMap<>(1);
                result.put("house_id", houseId);
                result.put("load", load);
                result.put("sys_time", System.currentTimeMillis());
                result.put("ts_start", this.timeWindow.getWindowStart());
                result.put("ts_end", this.timeWindow.getWindowEnd());

                this.outputCollector.emit(new Values(result, taskName, this.taskId));
            }
        }
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields("result", "topic", "partition");
        declarer.declare(fields);
    }

    private void sendTimeEvent(long timestamp){
        if(lastTimestamp != timestamp) {
            CurrentTimeEvent timeEvent = new CurrentTimeEvent(timestamp);
            this.esper.sendEvent(timeEvent);

            this.lastTimestamp = timestamp;
            this.lastDataReceived = System.currentTimeMillis();
        }
    }

    public void restoreTimewindow(){
        Optional<String> optionalTimeWindowStart = Optional.fromNullable(this.redisCache.get(firstTimestampKey));
        if(optionalTimeWindowStart.isPresent()){
            long timestamp = Long.valueOf(optionalTimeWindowStart.get());
            this.sendTimeEvent(timestamp);
            this.firstTimestampSaved = true;
            this.timeWindow.addEvent(new SimpleTimestamp(timestamp));
        }
    }

    private void initEsper(){
        URL queryPath = EsperFactory.class.getResource(esperQueryPath);
        try {
            this.query = Resources.toString(queryPath, StandardCharsets.UTF_8);
            this.query = query.replace("%MINUTES%", String.valueOf(this.windowSizeMinutes));
        } catch (IOException e){
            logger.error(e);
            System.exit(1);
        }

        EPServiceProvider eps = EsperFactory.makeEsperServiceProviderDebs(esperEngineName + "-" + taskId);
        EPAdministrator cepAdm = eps.getEPAdministrator();
        EPStatement cepStatement = cepAdm.createEPL(query);
        this.esperUpdateListener = new EsperUpdateListener();
        cepStatement.addListener(this.esperUpdateListener);
        this.esper = eps.getEPRuntime();
    }


}
