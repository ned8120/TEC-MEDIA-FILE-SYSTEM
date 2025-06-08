package com.tecmfs.disknode.server;

import com.tecmfs.disknode.config.DiskNodeConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class DiskNodeServer {
    private static final Logger logger = Logger.getLogger(DiskNodeServer.class.getName());
    private final DiskNodeConfig config;
    private HttpServer server;

    public DiskNodeServer(DiskNodeConfig config) throws IOException {
        this.config = config;
        InetSocketAddress addr = new InetSocketAddress(config.getIp(), config.getPort());
        server = HttpServer.create(addr, 0);
        server.createContext("/storeBlock", new StoreHandler());
        server.createContext("/getBlock", new GetHandler());
        server.setExecutor(null);
    }

    public void start() {
        try {
            server.start();
            logger.info("DiskNode listening on " + config.getIp() + ":" + config.getPort());
        } catch (Exception e) {
            logger.severe("Failed to start server: " + e.getMessage());
        }
    }

    class StoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String blockId = params.get("blockId");
                if (blockId == null || blockId.isEmpty() || !blockId.matches("[a-zA-Z0-9_-]+")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                byte[] data = exchange.getRequestBody().readAllBytes();
                if (data.length != config.getBlockSize()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                Path target = Paths.get(config.getStoragePath(), blockId + ".blk");
                Files.createDirectories(target.getParent());
                Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("OK".getBytes());
                }
            } catch (IOException e) {
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ex) {
                    logger.severe("Error sending response: " + ex.getMessage());
                }
                logger.severe("StoreHandler error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        }
    }

    class GetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String blockId = params.get("blockId");
                if (blockId == null || blockId.isEmpty() || !blockId.matches("[a-zA-Z0-9_-]+")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                Path file = Paths.get(config.getStoragePath(), blockId + ".blk");
                if (!Files.exists(file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] data = Files.readAllBytes(file);
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } catch (IOException e) {
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ex) {
                    logger.severe("Error sending response: " + ex.getMessage());
                }
                logger.severe("GetHandler error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] parts = param.split("=");
            if (parts.length > 1) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    public static void main(String[] args) {
        try {
            DiskNodeConfig cfg = DiskNodeConfig.loadFromFile("config.xml");
            new DiskNodeServer(cfg).start();
        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }
}
