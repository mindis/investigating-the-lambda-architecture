package ch.uzh.ddis.thesis.lambda_architecture.kafka;

import ch.uzh.ddis.thesis.lambda_architecture.kafka.producer.ProducerPerformanceTest;
import com.ecyrd.speed4j.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class ProducerPerformance {
    private final static Logger logger = LogManager.getLogger();
    private static final Marker performance = MarkerManager.getMarker("PERFORMANCE");

    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.put("metadata.broker.list", "localhost:9092");
            props.put("serializer.class", "kafka.serializer.StringEncoder");
            props.put("key.serializer.class", "kafka.serializer.StringEncoder");
            props.put("producer.type", "sync"); // use sync to not break the order of events

            int batchSize = 1;
            if(args.length > 0) {
                batchSize = new Integer(args[0]);
            }

            for (; batchSize <= 700; batchSize++) {
                StopWatch watchBatchSize = new StopWatch("kafka_byte_batchsize");

                ExecutorService executor = Executors.newFixedThreadPool(1);
                Runnable producerThread = new ProducerPerformanceTest("performance-test-string", "1000000000000,1377986401,68.451,0,11,0,0", props, batchSize);
                executor.execute(producerThread);

                executor.shutdown();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

                logger.info(performance, "topic={} batchSize={} duration={}", watchBatchSize.getTag(), batchSize, watchBatchSize.getTimeMicros());
            }

        } catch(InterruptedException ex){
            logger.error("producer thread crashed unexpectedly: %s", ex.getMessage());
        }
    }


}