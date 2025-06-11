package com.tecmfs.controller;

import com.tecmfs.common.models.Block;
import com.tecmfs.common.models.Stripe;
import com.tecmfs.controller.config.ControllerConfig;
import com.tecmfs.controller.models.StoredFile;
import com.tecmfs.controller.models.NodeStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta periódicamente el estado de los Disk Nodes, actualiza su blockCount
 * y reconstruye bloques faltantes si un nodo está inactivo.
 */
public class NodeMonitor {
    private static final Logger logger = Logger.getLogger(NodeMonitor.class.getName());

    private final MetadataManager metadataManager;
    private final FileDistributor distributor;
    private final List<String> nodeEndpoints;
    private final ScheduledExecutorService executor;
    private final int intervalSeconds;

    public NodeMonitor(MetadataManager metadataManager,
                       FileDistributor distributor,
                       ControllerConfig config) {
        this.metadataManager = metadataManager;
        this.distributor = distributor;
        this.nodeEndpoints = config.getDiskNodeEndpoints();
        this.intervalSeconds = config.getMonitorInterval();
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Inicia el monitoreo periódico.
     */
    public void start() {
        executor.scheduleAtFixedRate(this::checkAllNodes, 0, intervalSeconds, TimeUnit.SECONDS);
        logger.info("NodeMonitor iniciado con intervalo " + intervalSeconds + " segs");
    }

    private void checkAllNodes() {
        for (String endpoint : nodeEndpoints) {
            boolean alive = isNodeAlive(endpoint);
            metadataManager.updateNodeStatus(endpoint, alive);

            if (alive) {
                try {
                    int count = fetchBlockCount(endpoint);
                    // Actualiza el número de bloques almacenados
                    for (NodeStatus ns : metadataManager.getAllNodeStatus()) {
                        if (ns.getNodeId().equals(endpoint)) {
                            ns.setStoredBlockCount(count);
                            logger.info("Node " + endpoint + " blockCount=" + count);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.warning("No se pudo obtener blockCount de " + endpoint + ": " + e.getMessage());
                }
            } else {
                logger.warning("Node no disponible: " + endpoint);
                reconstructMissingBlocks(nodeEndpoints.indexOf(endpoint));
            }
        }
    }

    private boolean isNodeAlive(String endpoint) {
        try {
            URL url = new URL(endpoint + "/nodeStatus");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private int fetchBlockCount(String endpoint) throws IOException {
        URL url = new URL(endpoint + "/nodeStatus");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        InputStream is = conn.getInputStream();
        String body = new String(is.readAllBytes());
        conn.disconnect();
        Pattern p = Pattern.compile("\\\"blockCount\\\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(body);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new IOException("blockCount no encontrado en respuesta");
    }

    private void reconstructMissingBlocks(int missingNodeIndex) {
        List<StoredFile> files = metadataManager.getAllStoredFiles();
        for (StoredFile sf : files) {
            for (Stripe stripe : sf.getStripes()) {
                Block blk = stripe.getBlock(missingNodeIndex);
                if (blk == null || blk.isCorrupted()) {
                    try {
                        Block recovered = stripe.reconstructBlock(missingNodeIndex);
                        String endpoint = nodeEndpoints.get(missingNodeIndex);
                        distributor.sendBlock(endpoint, recovered);
                        logger.info("Reconstrucción: stripe=" + stripe.getStripeId()
                                + " pos=" + missingNodeIndex
                                + " -> enviado a " + endpoint);
                    } catch (Exception ex) {
                        logger.severe("Error reconstruyendo stripe " + stripe.getStripeId()
                                + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Detiene el monitoreo.
     */
    public void shutdown() {
        executor.shutdownNow();
        logger.info("NodeMonitor detenido");
    }
}
