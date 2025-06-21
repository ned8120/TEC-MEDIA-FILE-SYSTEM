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
import java.util.Set;
import java.util.logging.Logger;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.stream.Collectors;

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

        server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        server.createContext("/uploadFile", new UploadHandler());
        server.createContext("/downloadFile", new DownloadHandler());
        server.createContext("/nodeStatus", new NodeStatusHandler());
        server.createContext("/listFiles", new ListFilesHandler());
        server.createContext("/deleteFile", new DeleteHandler());
        server.createContext("/getNodes", new GetNodesHandler());
        server.createContext("/detailedClusterStatus", new DetailedClusterStatusHandler());
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
                        .collect(Collectors.toList());
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
     * Handler para consultar estado de los Disk Nodes (detallado).
     * Usa metadataManager, que NodeMonitor actualiza periódicamente.
     */
    class NodeStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            List<NodeStatus> statuses = metadataManager.getAllNodeStatus();
            String json = statuses.stream()
                    .map(ns -> String.format(
                            "{\"nodeId\":\"%s\",\"active\":%b,\"lastResponse\":\"%s\",\"blockCount\":%d}",
                            ns.getNodeId(), ns.isActive(), ns.getLastResponseTime(), ns.getStoredBlockCount()))
                    .collect(Collectors.joining(",", "[", "]"));

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    class DetailedClusterStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405,-1);
                ex.close();
                return;
            }
            Map<String,String> details = metadataManager.getAllDetailedNodeStatus();
            String json = details.entrySet().stream()
                    .map(e -> String.format(
                            "{\"nodeId\":\"%s\",\"details\":%s}",
                            e.getKey(), e.getValue()))
                    .collect(Collectors.joining(",","[","]"));
            byte[] b=json.getBytes();
            ex.getResponseHeaders().add("Content-Type","application/json");
            ex.sendResponseHeaders(200,b.length);
            try(OutputStream os=ex.getResponseBody()){os.write(b);}
        }
    }
    /**
     * Handler para devolver solo nodos activos con detalles extra.
     * Incluye nodeId, active, storedBlockCount y lastResponseTime.
     */
    class GetNodesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            // Obtener lista de NodeStatus actual desde MetadataManager
            List<NodeStatus> statuses = metadataManager.getAllNodeStatus();
            // Filtrar activos y convertir a JSON de objetos
            String json = statuses.stream()
                    .filter(NodeStatus::isActive)
                    .map(ns -> String.format(
                            "{\"nodeId\":\"%s\",\"active\":%b,\"storedBlocks\":%d,\"lastResponse\":\"%s\"}",
                            ns.getNodeId(), ns.isActive(), ns.getStoredBlockCount(), ns.getLastResponseTime()
                    ))
                    .collect(Collectors.joining(",", "[", "]"));

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
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

    public static void main(String[] args) {
        Logger logger = Logger.getLogger(ControllerServer.class.getName());
        try {
            ControllerConfig cfg = ControllerConfig.loadFromFile("config.xml");
            MetadataManager mm = new MetadataManager();
            NodeMonitor nm = new NodeMonitor(
                    "tecmfs-disknode/disknodes.xml",
                    cfg.getMonitorInterval(),
                    mm
            );
            FileDistributor fd = new FileDistributor(mm, cfg, nm);
            ControllerServer server = new ControllerServer(cfg, mm, fd, nm);
            server.start();
        } catch (Exception e) {
            logger.severe("Error al iniciar ControllerServer: " + e.getMessage());
        }
    }
}
