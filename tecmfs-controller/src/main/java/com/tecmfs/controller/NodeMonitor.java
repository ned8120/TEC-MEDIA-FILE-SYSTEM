package com.tecmfs.controller;

import com.tecmfs.disknode.config.DiskNodeConfig;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeMonitor {
    private static final Logger logger = Logger.getLogger(NodeMonitor.class.getName());

    private final List<DiskNodeConfig> nodeConfigs;
    private final ScheduledExecutorService executor;
    private final long intervalSeconds;
    private final Set<String> availableNodes = ConcurrentHashMap.newKeySet();

    // Para guardar el estado resumido en MetadataManager
    private final MetadataManager metadataManager;

    // Parámetros esperados (del primer nodo)
    private final int expectedBlockSize;
    private final long expectedCapacity;

    /**
     * Crea un monitor de nodos.
     *
     * @param xmlPath         ruta a disknodes.xml
     * @param intervalSeconds frecuencia de chequeo en segundos
     * @param mm              instancia de MetadataManager donde se guardarán los estados
     */
    public NodeMonitor(String xmlPath, long intervalSeconds, MetadataManager mm) throws Exception {
        this.nodeConfigs = DiskNodeConfig.loadAllFromFile(xmlPath);
        if (nodeConfigs.isEmpty()) {
            throw new IllegalStateException("No se encontraron configuraciones de Disk Nodes en " + xmlPath);
        }

        this.intervalSeconds = intervalSeconds;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.metadataManager = mm;

        // Tomamos como referencia el primer nodo
        DiskNodeConfig ref = nodeConfigs.get(0);
        this.expectedBlockSize = ref.getBlockSize();
        this.expectedCapacity = ref.getCapacityBytes();
        logger.info(String.format("Referencia de configuración: blockSize=%d, capacityBytes=%d",
                expectedBlockSize, expectedCapacity));
    }

    /** Inicia el monitoreo periódico */
    public void start() {
        executor.scheduleAtFixedRate(this::checkNodes, 0, intervalSeconds, TimeUnit.SECONDS);
        logger.info("NodeMonitor iniciado; chequeo cada " + intervalSeconds + " segs");
    }

    /** Detiene el monitoreo */
    public void shutdown() {
        executor.shutdownNow();
        logger.info("NodeMonitor detenido");
    }

    /** Ejecuta un ciclo de chequeo de todos los nodos */
    private void checkNodes() {
        Set<String> goodSummaries = new HashSet<>();

        // Patterns para extraer blockSize y capacityBytes de /nodeStatus
        Pattern patBS = Pattern.compile("\"blockSize\"\\s*:\\s*(\\d+)");
        Pattern patCap = Pattern.compile("\"capacityBytes\"\\s*:\\s*(\\d+)");

        for (DiskNodeConfig cfg : nodeConfigs) {
            String base = String.format("http://%s:%d", cfg.getIp(), cfg.getPort());
            String summaryUrl = base + "/nodeStatus";

            try {
                var conn = (HttpURLConnection) new URL(summaryUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                if (conn.getResponseCode() != 200) {
                    logger.warning("No responde 200 en /nodeStatus: " + summaryUrl);
                    conn.disconnect();
                    metadataManager.updateNodeStatus(summaryUrl, false);
                    continue;
                }

                String body;
                try (var is = conn.getInputStream()) {
                    body = new String(is.readAllBytes());
                }
                conn.disconnect();

                Matcher mBS = patBS.matcher(body);
                Matcher mCap = patCap.matcher(body);
                if (!mBS.find() || !mCap.find()) {
                    logger.warning("JSON inesperado en /nodeStatus de " + summaryUrl);
                    metadataManager.updateNodeStatus(summaryUrl, false);
                    continue;
                }

                int bs = Integer.parseInt(mBS.group(1));
                long cap = Long.parseLong(mCap.group(1));
                if (bs != expectedBlockSize || cap != expectedCapacity) {
                    logger.warning("Config difiere en " + summaryUrl + " (bs=" + bs + ", cap=" + cap + ")");
                    metadataManager.updateNodeStatus(summaryUrl, false);
                    continue;
                }

                // Nodo OK: actualizo summary en MetadataManager
                metadataManager.updateNodeStatus(summaryUrl, true);
                goodSummaries.add(base);

                // Ahora solicito el estado detallado
                String detailUrl = base + "/detailedNodeStatus";
                try {
                    var c2 = (HttpURLConnection) new URL(detailUrl).openConnection();
                    c2.setRequestMethod("GET");
                    c2.setConnectTimeout(2000);
                    if (c2.getResponseCode() == 200) {
                        String detailJson;
                        try (var is2 = c2.getInputStream()) {
                            detailJson = new String(is2.readAllBytes());
                        }
                        metadataManager.updateDetailedNodeStatus(summaryUrl, detailJson);
                    } else {
                        logger.warning("No responde 200 en /detailedNodeStatus: " + detailUrl);
                    }
                    c2.disconnect();
                } catch (IOException e) {
                    logger.warning("Error en detallado de " + detailUrl + ": " + e.getMessage());
                }

            } catch (IOException e) {
                logger.warning("Error contactando a " + summaryUrl + ": " + e.getMessage());
                metadataManager.updateNodeStatus(summaryUrl, false);
            }
        }

        // Actualizo la lista interna
        availableNodes.clear();
        availableNodes.addAll(goodSummaries);
        logger.info("Nodos disponibles: " + availableNodes);
    }

    /** Devuelve la lista de endpoints de nodeStatus que están activos */
    public Set<String> getAvailableNodes() {
        return Collections.unmodifiableSet(availableNodes);
    }

    /** Opcional: si quieres exponerlos directamente */
    public Map<String,String> getAllDetailedNodeStatusRaw() {
        return metadataManager.getAllDetailedNodeStatus();
    }
}
