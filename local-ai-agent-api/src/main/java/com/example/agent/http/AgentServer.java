package com.example.agent.http;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thin wrapper around the JDK built-in HttpServer. Port 0 picks a free port (useful in tests). */
public final class AgentServer {

    private final HttpServer server;
    private final ExecutorService executor;

    public AgentServer(int port, HttpHandler handler) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.createContext("/", handler); // every path goes through the controller's own router
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
        executor.shutdown();
    }

    public int port() {
        return server.getAddress().getPort();
    }
}
