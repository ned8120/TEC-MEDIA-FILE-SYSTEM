package com.tecmfs.disknode.server;

import com.tecmfs.disknode.config.DiskNodeConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Servidor HTTP liviano que maneja almacenamiento y recuperación de bloques en disco.
 */
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
        server.createContext("/deleteBlock", new DeleteHandler());

        server.setExecutor(null);
    }

    /**
     * Inicia el servidor y comienza a escuchar peticiones.
     */
    public void start() {
        try {
            server.start();
            logger.info("DiskNode escuchando en " + config.getIp() + ":" + config.getPort());
        } catch (Exception e) {
            logger.severe("Error al iniciar el servidor: " + e.getMessage());
        }
    }



    /**
     * Handler para almacenar un bloque.
     */
    class StoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"POST".equals(exchange.getRequestMethod())) {
                try {
                    exchange.sendResponseHeaders(405, -1);
                } catch (IOException e) {
                    logger.severe("Error al responder método no permitido: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String blockId = params.get("blockId");
            if (blockId == null || blockId.isEmpty() || !blockId.matches("[a-zA-Z0-9_-]+")) {
                try {
                    exchange.sendResponseHeaders(400, -1);
                } catch (IOException e) {
                    logger.severe("Error al responder solicitud inválida: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Path target = Paths.get(config.getStoragePath(), blockId + ".blk");
            if (Files.exists(target)) {
                logger.warning("El bloque " + blockId + " ya existe y será sobrescrito.");
            }
            try {
                Files.createDirectories(target.getParent());
                try (InputStream is = new BufferedInputStream(exchange.getRequestBody());
                     OutputStream os = new BufferedOutputStream(
                             Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = is.read(buffer)) != -1) {
                        total += read;
                        os.write(buffer, 0, read);
                    }
                    os.flush();
                    if (total != config.getBlockSize()) {
                        logger.warning("Tamaño de datos recibido (" + total + ") difiere de blockSize (" + config.getBlockSize() + ").");
                    }
                }

                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os2 = exchange.getResponseBody()) {
                    os2.write("OK".getBytes());
                }
            } catch (IOException e) {
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ex) {
                    logger.severe("Error al responder: " + ex.getMessage());
                }
                logger.severe("StoreHandler error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * Handler para recuperar un bloque.
     */

    class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"DELETE".equals(exchange.getRequestMethod())) {
                try {
                    exchange.sendResponseHeaders(405, -1);
                } catch (IOException e) {
                    logger.severe("Método no permitido: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String blockId = params.get("blockId");
            if (blockId == null || blockId.isEmpty()) {
                try {
                    exchange.sendResponseHeaders(400, -1);
                } catch (IOException e) {
                    logger.severe("Solicitud inválida: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Path file = Paths.get(config.getStoragePath(), blockId + ".blk");
            try {
                if (Files.deleteIfExists(file)) {
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write("Bloque eliminado".getBytes());
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (IOException e) {
                logger.severe("Error al eliminar bloque: " + e.getMessage());
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ex) {
                    logger.severe("Error al responder: " + ex.getMessage());
                }
            } finally {
                exchange.close();
            }
        }
    }

    class GetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"GET".equals(exchange.getRequestMethod())) {
                try {
                    exchange.sendResponseHeaders(405, -1);
                } catch (IOException e) {
                    logger.severe("Error al responder método no permitido: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String blockId = params.get("blockId");
            if (blockId == null || blockId.isEmpty() || !blockId.matches("[a-zA-Z0-9_-]+")) {
                try {
                    exchange.sendResponseHeaders(400, -1);
                } catch (IOException e) {
                    logger.severe("Error al responder solicitud inválida: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            Path file = Paths.get(config.getStoragePath(), blockId + ".blk");
            if (!Files.exists(file)) {
                try {
                    exchange.sendResponseHeaders(404, -1);
                } catch (IOException e) {
                    logger.severe("Error al responder no encontrado: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            try {
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                long fileSize = Files.size(file);
                exchange.sendResponseHeaders(200, fileSize);
                try (InputStream is = new BufferedInputStream(Files.newInputStream(file));
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ex) {
                    logger.severe("Error al responder: " + ex.getMessage());
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
            if (parts.length > 1) map.put(parts[0], parts[1]);
        }
        return map;
    }

    /**
     * Punto de entrada de la aplicación.
     */
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(DiskNodeServer.class.getName());
        try {
            List<DiskNodeConfig> configs = DiskNodeConfig.loadAllFromFile("config.xml");
            for (DiskNodeConfig cfg : configs) {
                new Thread(() -> {
                    try {
                        new DiskNodeServer(cfg).start();
                    } catch (Exception e) {
                        logger.severe("Error al iniciar nodo en puerto " + cfg.getPort() + ": " + e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            logger.severe("Error al leer la configuración: " + e.getMessage());
        }
    }

}