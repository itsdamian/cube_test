package com.currency.demo.feed;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local stand-in for an exchange: accepts WebSocket clients on 127.0.0.1 (random port),
 * records what they send, and "pumps" a message every 100 ms while pumping is on.
 * Turning the pump off keeps connections open but silent - a half-open connection as seen
 * from the client.
 */
final class FakeExchangeServer extends WebSocketServer {

    private final AtomicInteger connectionsOpened = new AtomicInteger();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final ScheduledExecutorService pump = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean pumping;
    private volatile boolean closeOnOpen;
    private volatile String pumpMessage;

    FakeExchangeServer(String pumpMessage) {
        this(0, pumpMessage);
    }

    /** @param port 0 = pick a free port */
    FakeExchangeServer(int port, String pumpMessage) {
        super(new InetSocketAddress("127.0.0.1", port));
        this.pumpMessage = pumpMessage;
        setReuseAddr(true);
    }

    void startAndWait() throws InterruptedException {
        start();
        if (!started.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("fake exchange did not start");
        }
        pump.scheduleAtFixedRate(() -> {
            if (pumping) {
                broadcast(pumpMessage);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    void shutdown() throws InterruptedException {
        pump.shutdownNow();
        stop(1000);
    }

    URI uri() {
        return URI.create("ws://127.0.0.1:" + getPort() + "/");
    }

    /** Accept each connection and immediately close it, before sending anything. */
    void closeOnOpen(boolean on) {
        closeOnOpen = on;
    }

    void pumping(boolean on) {
        pumping = on;
    }

    /** Server-initiated clean close of every connection. */
    void closeAllConnections() {
        getConnections().forEach(c -> c.close(1001, "going away"));
    }

    int connectionsOpened() {
        return connectionsOpened.get();
    }

    List<String> received() {
        return received;
    }

    @Override
    public void onStart() {
        started.countDown();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectionsOpened.incrementAndGet();
        if (closeOnOpen) {
            conn.close(1011, "go away");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        received.add(message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }
}
