package ch.uzh.ddis.thesis.lambda_architecture.speed.spout;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import ch.uzh.ddis.thesis.lambda_architecture.data.IDataEntry;
import ch.uzh.ddis.thesis.lambda_architecture.data.IDataFactory;
import com.ecyrd.speed4j.StopWatch;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The netty spout is responsible to consume messages from the coordination layer and send these messages to
 * the topology. The spout follows a non-blocking design, where netty consumers are started in a seperate thread
 * and write messages to a synchronized queue (NettyQueue). Each time Storm calls the nextTuple method of this
 * spout, the spout will check whether new messages are available in this queue and transfer one each time the function
 * is called.
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class NettySpout extends BaseRichSpout {
    private static final Logger logger = LogManager.getLogger();
    private static final Marker performance = MarkerManager.getMarker("PERFORMANCE");
    private static final Marker remoteDebug = MarkerManager.getMarker("DEBUGFLUME");

    private static final int maxConnectionRetry = 50;
    private static final int maxChannelRetry = 5000;
    private static final long shutdownWaitThreshold = 5 * 60 * 1000;
    private static final int maxPendingWait = 5;
    private static final int maxPending = 500;

    private final ArrayList<HostAndPort> hosts;
    private final IDataFactory dataFactory;

    private SpoutOutputCollector outputCollector;
    private TopologyContext context;
    private Map config;

    private ChannelFuture channelFuture;
    private EventLoopGroup workerGroup;

    private boolean finished = false;

    private final NettyQueue nettyQueue = new NettyQueue();


    private int connectionFailedCounter=0;
    private int channelFailedCounter=0;

    private long lastDataEmitted = 0;
    private boolean stopped = false;

    private long processCounter = 0;
    private StopWatch processWatch;

    public NettySpout(ArrayList<HostAndPort> hosts, IDataFactory dataFactory) {
        this.hosts = hosts;
        this.dataFactory = dataFactory;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields fields = new Fields("data", "partition");
        outputFieldsDeclarer.declare(fields);
    }

    @Override
    public void open(Map conf, TopologyContext topologyContext, SpoutOutputCollector spoutOutputCollector) {

        this.outputCollector = spoutOutputCollector;
        this.config = conf;
        this.context = topologyContext;

        this.workerGroup = new NioEventLoopGroup();

        this.connect();
    }

    /**
     * Manages the connection to the coordination layer. Please note that this is basically a non-blocking
     * recursive function. Each time a consumer is started, a listener is attached, that calls this method in
     * case a failure occured and it gets destroyed. Therefore the connect manages the asynchronous connection
     * bootstrap.
     */
    private void connect(){
        final HostAndPort host = this.hosts.get(context.getThisTaskIndex());

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("frameDecoder", new LineBasedFrameDecoder(120 * 150));
                ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                ch.pipeline().addLast(new NettyClient(nettyQueue));
                ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
            }
        });

        try {
            this.channelFuture = b.connect(host.getHostText(), host.getPort()).sync();
            this.channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    logger.warn("1. channel was closed: finished={}, success={}, open={}", finished, channelFuture.isSuccess(), channelFuture.channel().isOpen());
                    if (!finished && !channelFuture.channel().isOpen()) {
                        channelFuture.channel().eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                channelFailedCounter++;
                                if(channelFailedCounter <= maxChannelRetry) {
                                    logger.warn("connection lost to netty producer, reconnecting (host={})", host.toString());
                                    connect();
                                }
                            }
                        }, 10, TimeUnit.MILLISECONDS);
                    }
                }
            });
            this.channelFuture.channel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    logger.warn("channel was closed: finished={}, success={}, open={}", finished, channelFuture.isSuccess(), channelFuture.channel().isOpen());
                    if (!finished && !channelFuture.channel().isOpen()) {
                        channelFuture.channel().eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                channelFailedCounter++;
                                if(channelFailedCounter <= maxChannelRetry) {
                                    logger.warn("connection lost to netty producer, reconnecting (host={})", host.toString());
                                    connect();
                                }
                            }
                        }, 10, TimeUnit.MILLISECONDS);
                    }
                }
            });
        } catch (InterruptedException e){
            logger.error(e);
        } catch (Exception e){
            this.connectionFailedCounter++;
            if((this.connectionFailedCounter < maxConnectionRetry) || (this.channelFailedCounter > 0 && this.connectionFailedCounter > maxConnectionRetry/2)){
                logger.warn("connection failed to netty producer, trying to connect again. (host={})", host.toString());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie){
                    logger.error(ie);
                }

                connect();
            }
        }

    }

    /**
     * This method is periodically called by Storm and will emit tuples to the topology in case there are any available.
     */
    @Override
    public void nextTuple() {
        Optional<String> optionalData = Optional.fromNullable(this.nettyQueue.queue.poll());
        if (optionalData.isPresent()) {
            IDataEntry data = this.dataFactory.makeDataEntryFromCSV(optionalData.get());
            this.outputCollector.emit(new Values(data.toString(), data.getPartitionKey()), data.getId());

            this.lastDataEmitted = System.currentTimeMillis();

            if (this.processCounter == 0) {
                this.processWatch = new StopWatch();
            }

            this.processCounter++;
            if (this.processCounter % 1000 == 0) {
                this.processWatch.stop();
                logger.info(performance, "topic={} stepSize={} duration={} threadId={}",
                        "nettySpoutMessageThroughput", "1000", this.processWatch.getTimeMicros(), this.context.getThisTaskIndex());
                this.processWatch = new StopWatch();
            }
        } else {
            if (!stopped && lastDataEmitted != 0 && System.currentTimeMillis() - lastDataEmitted > shutdownWaitThreshold) {
                this.close();
                this.stopped = true;
            }
        }
    }

    /**
     * Acknowledgements are not supported by this spout. Also replaying messages is not possible.
     * @param msgId
     */
    @Override
    public void ack(Object msgId) {
        // no-op
    }

    /**
     * Acknowledgements are not supported by this spout. Also replaying messages is not possible.
     * @param msgId
     */
    @Override
    public void fail(Object msgId) {
        // no-op
    }

    /**
     * Closes the connection to the coordination layer and terminates the spout.
     */
    @Override
    public void close() {
        logger.info("close spout called");
        super.close();
        this.finished = true;

        try {
            this.channelFuture.channel().closeFuture();
            this.workerGroup.shutdownGracefully();
        } catch (NullPointerException e){
            logger.error(e);
        }
    }
}
