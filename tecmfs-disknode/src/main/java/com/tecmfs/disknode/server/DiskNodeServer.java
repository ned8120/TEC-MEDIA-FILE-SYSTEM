package com.tecmfs.disknode.server;

import com.tecmfs.disknode.config.DiskNodeConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Servidor HTTP liviano que maneja almacenamiento y recuperación de bloques en disco.
 */
public class DiskNodeServer {
    private static final Logger logger = Logger.getLogger(DiskNodeServer.class.getName());
    private final DiskNodeConfig config;
    private final HttpServer server;

    public DiskNodeServer(DiskNodeConfig config) throws IOException {
        this.config = config;
        InetSocketAddress addr = new InetSocketAddress(config.getIp(), config.getPort());
        server = HttpServer.create(addr, 0);
        server.createContext("/storeBlock", new StoreHandler());
        server.createContext("/getBlock", new GetHandler());
        server.createContext("/deleteBlock", new DeleteHandler());
        server.createContext("/nodeStatus", new StatusHandler());
        server.createContext("/detailedNodeStatus", new DetailedStatusHandler()); // Nuevo endpoint
        server.createContext("/shutdown", new ShutdownHandler());
        server.setExecutor(null);

        server.setExecutor(null);
    }

    /**
     * Inicia el servidor y comienza a escuchar peticiones.
     */
    public void start() {
        try {
            server.start();
            logger.info("DiskNode iniciado en " + config.getIp() + ":" + config.getPort()
                    + " con capacidad=" + config.getCapacityBytes() + " bytes");
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

            Path root = Paths.get(config.getStoragePath());
            try {
                long usedBytes = Files.list(root)
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                // Prever tamaño de bloque fija
                if (usedBytes + config.getBlockSize() > config.getCapacityBytes()) {
                    exchange.sendResponseHeaders(507, -1); // Storage Insufficient
                    return;
                }
            } catch (IOException e) {
                logger.warning("No se pudo calcular espacio usado: " + e.getMessage());
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
                logger.info("Bloque guardado exitosamente en: " + target.toAbsolutePath());

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
     * Handler para estado del nodo.
     * Devuelve JSON con: status, blockCount, usedBytes, blockSize, capacityBytes.
     */
    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path dir = Paths.get(config.getStoragePath());
            long blockCount;
            long usedBytes;
            // Contar sólo archivos .blk
            try (Stream<Path> listing = Files.list(dir)) {
                blockCount = listing
                        .filter(p -> p.toString().endsWith(".blk"))
                        .count();
            }
            // Sumar todos los bytes de archivos regulares en subdirectorios
            try (Stream<Path> walk = Files.walk(dir)) {
                usedBytes = walk
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> p.toFile().length())
                        .sum();
            }

            String json = String.format(
                    "{\"status\":\"active\","
                            + "\"blockCount\":%d,"
                            + "\"usedBytes\":%d,"
                            + "\"blockSize\":%d,"
                            + "\"capacityBytes\":%d}",
                    blockCount,
                    usedBytes,
                    config.getBlockSize(),
                    config.getCapacityBytes()
            );

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    // --- Detailed Status Handler ---
    class DetailedStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path dir = Paths.get(config.getStoragePath());
            List<String> entries;
            try (Stream<Path> files = Files.list(dir)) {
                entries = files.filter(p -> p.toString().endsWith(".blk"))
                        .map(p -> {
                            try {
                                String fileName = p.getFileName().toString();
                                String blockId = fileName.replaceFirst("\\.blk$", "");
                                String type = blockId.contains("_p") ? "PARITY" : "DATA";
                                long size = Files.size(p);
                                long lm = Files.getLastModifiedTime(p).toMillis();
                                return String.format(
                                        "{\"blockId\":\"%s\",\"type\":\"%s\",\"size\":%d,\"lastModified\":%d}",
                                        blockId, type, size, lm);
                            } catch (IOException e) {
                                return null;
                            }
                        })
                        .filter(s -> s != null)
                        .collect(Collectors.toList());
            }

            String json = "[" + String.join(",", entries) + "]";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    /**
     * Handler para apagar el servidor de forma controlada.
     */
    class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
            new Thread(() -> {
                try { Thread.sleep(200); server.stop(0); }
                catch (InterruptedException ignored) {}
            }).start();
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

    /**
     * Punto de entrada de la aplicación.
     */
    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        // Ejemplo de invocación en modo personalizado
        if (args.length >= 2 && args[0].startsWith("--port=") && args[1].startsWith("--storage=")) {
            int port = Integer.parseInt(args[0].substring(7));
            String storage = args[1].substring(10);
            DiskNodeConfig cfg = new DiskNodeConfig("127.0.0.1", port, storage, 4096, 1073741824);
            new DiskNodeServer(cfg).start();
            return;
        }
        // Modo por defecto: cargar múltiples nodos desde XML
        List<DiskNodeConfig> configs = DiskNodeConfig.loadAllFromFile("tecmfs-disknode/disknodes.xml");
        for (DiskNodeConfig cfg : configs) {
            new Thread(() -> {
                try { new DiskNodeServer(cfg).start(); }
                catch (IOException e) { logger.severe("Error al iniciar nodo: " + e.getMessage()); }
            }).start();
        }
    }
}