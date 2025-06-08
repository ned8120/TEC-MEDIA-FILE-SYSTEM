package com.tecmfs.disknode.server;
import com.tecmfs.disknode.config.DiskNodeConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

public class DiskNodeServer {
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
        server.start();
        System.out.println("DiskNode listening on " + config.getIp() + ":" + config.getPort());
    }

    // Handler para almacenar un bloque
    class StoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // obtener parámetros query: blockId y type
            Map<String,String> params = queryToMap(exchange.getRequestURI().getQuery());
            String blockId = params.get("blockId");
            if (blockId == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            // leer cuerpo
            byte[] data = exchange.getRequestBody().readAllBytes();
            Path target = Paths.get(config.getStoragePath(), blockId + ".blk");
            Files.createDirectories(target.getParent());
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("OK".getBytes());
            }
        }
    }

    // Handler para recuperar un bloque
    class GetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String,String> params = queryToMap(exchange.getRequestURI().getQuery());
            String blockId = params.get("blockId");
            if (blockId == null) {
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
        }
    }

    // Util para parsear query string
    private static Map<String,String> queryToMap(String query) {
        Map<String,String> map = new HashMap<>();
        if (query == null) return map;
        for (String param : query.split("&")) {
            String[] parts = param.split("=");
            if (parts.length > 1) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        DiskNodeConfig cfg = DiskNodeConfig.loadFromFile("config.xml");
        new DiskNodeServer(cfg).start();
    }
}
