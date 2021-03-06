package ch.uzh.ddis.thesis.lambda_architecture.speed.spout;

import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Netty clients are asynchronous and in case of any failure a new client is started to resume operations.
 * Therefore the netty queue provides a synchronized queue for all clients started.
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class NettyQueue extends ChannelInboundHandlerAdapter implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger();

    private static final int max_buffer = 5000;

    public final ArrayBlockingQueue<String> queue;

    public NettyQueue() {
        this.queue = new ArrayBlockingQueue<>(max_buffer);
    }
}
