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
    private final HttpServer server;

    public DiskNodeServer(DiskNodeConfig config) throws IOException {
        this.config = config;
        InetSocketAddress addr = new InetSocketAddress(config.getIp(), config.getPort());
        server = HttpServer.create(addr, 0);
        server.createContext("/storeBlock", new StoreHandler());
        server.createContext("/getBlock", new GetHandler());
        server.createContext("/deleteBlock", new DeleteHandler());
        server.createContext("/nodeStatus", new StatusHandler());
        server.createContext("/shutdown", new ShutdownHandler());

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
                    exchange.sendResponseHeaders(400, -1); // Storage Insufficient
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
class StatusHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }

            long used = Files.list(Paths.get(config.getStoragePath()))
                    .mapToLong(p -> p.toFile().length()).sum();
            String json = String.format(
                    "{\"status\":\"active\",\"blockSize\":%d,\"capacityBytes\":%d,\"usedBytes\":%d}",
                    config.getBlockSize(), config.getCapacityBytes(), used);

            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, json.getBytes().length);
            try (OutputStream os = ex.getResponseBody()) { os.write(json.getBytes()); }
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

    class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            logger.info("Solicitud de apagado recibida.");
            if (!"POST".equals(exchange.getRequestMethod())) {
                try {
                    exchange.sendResponseHeaders(405, -1);
                } catch (IOException e) {
                    logger.severe("Método no permitido: " + e.getMessage());
                } finally {
                    exchange.close();
                }
                return;
            }

            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("Apagando nodo...".getBytes());
                exchange.getResponseBody().close();

                // Apagar servidor después de un pequeño retraso para enviar la respuesta
                new Thread(() -> {
                    try {
                        Thread.sleep(200); // permite que la respuesta llegue antes del apagado
                        server.stop(0);
                        logger.info("Nodo apagado correctamente.");
                    } catch (InterruptedException ignored) {}
                }).start();

            } catch (IOException e) {
                logger.severe("Error durante apagado: " + e.getMessage());
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

        // Modo personalizado: iniciar un solo nodo
        if (args.length > 0 && args[0].startsWith("--port=")) {
            String portStr = null, storagePath = null;
            for (String arg : args) {
                if (arg.startsWith("--port=")) {
                    portStr = arg.substring("--port=".length());
                } else if (arg.startsWith("--storage=")) {
                    storagePath = arg.substring("--storage=".length());
                }
            }

            if (portStr == null || storagePath == null) {
                logger.severe("Faltan argumentos. Uso esperado: --port=8001 --storage=./storage1");
                return;
            }

            try {
                int port = Integer.parseInt(portStr);
                DiskNodeConfig cfg = new DiskNodeConfig("127.0.0.1", port, storagePath, 4096,1073741824); // Usa blockSize fijo
                new DiskNodeServer(cfg).start();
            } catch (Exception e) {
                logger.severe("Error al iniciar nodo personalizado: " + e.getMessage());
            }
            return;
        }

        // Modo por defecto: cargar desde XML y omitir algunos nodos
        try {
            Set<Integer> omitidos = new HashSet<>();
            for (String arg : args) {
                omitidos.add(Integer.parseInt(arg)); // Puertos omitidos
            }

            List<DiskNodeConfig> configs = DiskNodeConfig.loadAllFromFile("tecmfs-disknode/disknodes.xml");
            for (DiskNodeConfig cfg : configs) {
                if (omitidos.contains(cfg.getPort())) {
                    logger.info("Nodo en puerto " + cfg.getPort() + " fue omitido por simulación de falla.");
                    continue;
                }

                new Thread(() -> {
                    try {
                        new DiskNodeServer(cfg).start();
                    } catch (Exception e) {
                        logger.severe("Error al iniciar nodo en puerto " + cfg.getPort() + ": " + e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            logger.severe("Error al leer configuración: " + e.getMessage());
        }
    }

}