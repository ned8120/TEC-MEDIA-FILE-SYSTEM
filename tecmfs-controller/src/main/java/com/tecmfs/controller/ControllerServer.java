package com.tecmfs.controller;

import com.tecmfs.common.models.Block;
import com.tecmfs.common.models.Stripe;
import com.tecmfs.controller.config.ControllerConfig;
import com.tecmfs.controller.models.NodeStatus;
import com.tecmfs.controller.models.StoredFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.net.URL;
import java.net.HttpURLConnection;
/**
 * Servidor HTTP del Controller Node.
 * Maneja endpoints para subir, descargar archivos y consultar estado de nodos.
 */
public class ControllerServer {
    private static final Logger logger = Logger.getLogger(ControllerServer.class.getName());

    private final ControllerConfig config;
    private final MetadataManager metadataManager;
    private final FileDistributor distributor;
    private final NodeMonitor nodeMonitor;
    private HttpServer server;

    public ControllerServer(ControllerConfig config,
                            MetadataManager metadataManager,
                            FileDistributor distributor,
                            NodeMonitor nodeMonitor) throws IOException {
        this.config = config;
        this.metadataManager = metadataManager;
        this.distributor = distributor;
        this.nodeMonitor = nodeMonitor;

        InetSocketAddress addr = new InetSocketAddress(config.getPort());
        server = HttpServer.create(addr, 0);
        server.createContext("/uploadFile", new UploadHandler());
        server.createContext("/downloadFile", new DownloadHandler());
        server.createContext("/nodeStatus", new NodeStatusHandler());
        server.createContext("/listFiles", new ListFilesHandler());
        server.createContext("/deleteFile", new DeleteHandler());

        server.setExecutor(null);
    }

    /**
     * Inicia el servidor HTTP y el monitoreo de nodos.
     */
    public void start() {
        server.start();
        logger.info("ControllerServer escuchando en puerto " + config.getPort());
        nodeMonitor.start();
    }

    /**
     * Handler para subir un archivo PDF.
     */
    class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String fileName = params.get("fileName");
            if (fileName == null || fileName.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            try (InputStream is = new BufferedInputStream(exchange.getRequestBody())) {
                String fileId = distributor.distribute(fileName, is);
                String response = "{\"fileId\":\"" + fileId + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                byte[] bytes = response.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                logger.severe("Error en UploadHandler: " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * Handler para descargar un archivo completo.
     */
    class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String fileId = params.get("fileId");
            if (fileId == null || fileId.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            StoredFile sf = metadataManager.getStoredFile(fileId);
            if (sf == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            // Reconstrucción: FileDistributor debe implementar reconstruct()
            try (InputStream reconstructed = distributor.reconstruct(fileId)) {
                exchange.getResponseHeaders().add("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, 0);
                byte[] buf = new byte[8192];
                int len;
                while ((len = reconstructed.read(buf)) != -1) {
                    exchange.getResponseBody().write(buf, 0, len);
                }
            } catch (NoSuchMethodError e) {
                logger.severe("FileDistributor.reconstruct no implementado");
                exchange.sendResponseHeaders(501, -1);
            } catch (Exception e) {
                logger.severe("Error en DownloadHandler: " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
    class ListFilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String nameFilter = params.get("name"); // puede ser null

            List<StoredFile> files = metadataManager.getAllStoredFiles();


            if (nameFilter != null && !nameFilter.isEmpty()) {
                String lowerFilter = nameFilter.toLowerCase();
                files = files.stream()
                        .filter(f -> f.getFileName().toLowerCase().contains(lowerFilter))
                        .toList(); // Requiere Java 16+, usa `.collect(Collectors.toList())` si usas Java 8-11
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < files.size(); i++) {
                StoredFile f = files.get(i);
                sb.append("{\"fileId\":\"").append(f.getFileId())
                        .append("\",\"fileName\":\"").append(f.getFileName()).append("\"}");
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        }
    }
    class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"DELETE".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String fileId = params.get("fileId");
            if (fileId == null || fileId.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            StoredFile sf = metadataManager.getStoredFile(fileId);
            if (sf == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            // Enviar orden de borrar a cada nodo
            for (Stripe stripe : sf.getStripes()) {
                for (int i = 0; i < config.getDiskNodeEndpoints().size(); i++) {
                    Block b = stripe.getBlock(i);
                    if (b != null) {
                        try {
                            String nodeUrl = config.getDiskNodeEndpoints().get(i);
                            String fullUrl = nodeUrl + "/deleteBlock?blockId=" + b.getBlockId();
                            logger.info("Enviando DELETE a: " + fullUrl);

                            try {
                                URL url = new URL(fullUrl);
                                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                                c.setRequestMethod("DELETE");
                                int responseCode = c.getResponseCode();
                                logger.info("Respuesta desde " + nodeUrl + ": " + responseCode);
                                c.disconnect();
                            } catch (Exception e) {
                                logger.warning("Error al enviar DELETE a " + fullUrl + ": " + e.getMessage());
                            }

                        } catch (Exception e) {
                            logger.warning("Error eliminando bloque en nodo " + i + ": " + e.getMessage());
                        }
                    }
                }
            }

            metadataManager.removeFile(fileId);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }
    }

    /**
     * Handler para consultar estado de los Disk Nodes.
     */
    class NodeStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logger.info("Solicitud recibida: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                List<NodeStatus> statuses = metadataManager.getAllNodeStatus();
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < statuses.size(); i++) {
                    NodeStatus ns = statuses.get(i);
                    sb.append("{\"nodeId\":\"")
                            .append(ns.getNodeId())
                            .append("\",\"active\":")
                            .append(ns.isActive())
                            .append(",\"lastResponse\":\"")
                            .append(ns.getLastResponseTime())
                            .append("\",\"blockCount\":")
                            .append(ns.getStoredBlockCount())
                            .append("}");
                    if (i < statuses.size() - 1) sb.append(",");
                }
                sb.append("]");
                byte[] bytes = sb.toString().getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                logger.severe("Error en NodeStatusHandler: " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * Parseo simple de query string.
     */
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
     * Punto de entrada. Carga configuración y arranca servicios.
     */
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(ControllerServer.class.getName());
        try {
            ControllerConfig cfg = ControllerConfig.loadFromFile("config.xml");
            MetadataManager mm = new MetadataManager();
            FileDistributor fd = new FileDistributor(mm, cfg);
            NodeMonitor nm = new NodeMonitor(mm, fd, cfg);
            ControllerServer server = new ControllerServer(cfg, mm, fd, nm);
            server.start();
        } catch (Exception e) {
            logger.severe("Error al iniciar ControllerServer: " + e.getMessage());
        }
    }
}

