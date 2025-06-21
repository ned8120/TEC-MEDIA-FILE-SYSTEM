package com.tecmfs.controller;

import com.tecmfs.disknode.config.DiskNodeConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitorea el estado y la configuración de los Disk Nodes definidos en disknodes.xml.
 * Mantiene una lista de nodos disponibles que han respondido OK y cuyos parámetros coinciden.
 */
public class NodeMonitor {
    private static final Logger logger = Logger.getLogger(NodeMonitor.class.getName());

    private final List<DiskNodeConfig> nodeConfigs;
    private final ScheduledExecutorService executor;
    private final long intervalSeconds;
    private final Set<String> availableNodes;

    // Valores esperados (tomados del primer nodo al inicio)
    private final int expectedBlockSize;
    private final long expectedCapacity;

    /**
     * @param xmlPath         ruta a disknodes.xml
     * @param intervalSeconds frecuencia de chequeo en segundos
     */
    public NodeMonitor(String xmlPath, long intervalSeconds) throws Exception {
        this.nodeConfigs = DiskNodeConfig.loadAllFromFile(xmlPath);
        if (nodeConfigs.isEmpty()) {
            throw new IllegalStateException("No se encontraron configuraciones de Disk Nodes en " + xmlPath);
        }
        this.intervalSeconds = intervalSeconds;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.availableNodes = ConcurrentHashMap.newKeySet();

        // Tomamos como referencia el primer nodo
        DiskNodeConfig ref = nodeConfigs.get(0);
        this.expectedBlockSize = ref.getBlockSize();
        this.expectedCapacity = ref.getCapacityBytes();
        logger.info(String.format("Referencia de configuración: blockSize=%d, capacityBytes=%d",
                expectedBlockSize, expectedCapacity));
    }

    /**
     * Inicia el monitoreo periódico.
     */
    public void start() {
        executor.scheduleAtFixedRate(this::checkNodes, 0, intervalSeconds, TimeUnit.SECONDS);
        logger.info("NodeMonitor iniciado; chequeo cada " + intervalSeconds + " segs");
    }

    /**
     * Detiene el monitoreo.
     */
    public void shutdown() {
        executor.shutdownNow();
        logger.info("NodeMonitor detenido");
    }

    /**
     * Realiza la consulta a cada nodo y actualiza la lista de disponibles.
     */
    private void checkNodes() {
        Set<String> good = new HashSet<>();
        Pattern patBS = Pattern.compile("\\\"blockSize\\\"\\s*:\\s*(\\d+)");
        Pattern patCap = Pattern.compile("\\\"capacityBytes\\\"\\s*:\\s*(\\d+)");

        for (DiskNodeConfig cfg : nodeConfigs) {
            String endpoint = String.format("http://%s:%d/nodeStatus", cfg.getIp(), cfg.getPort());
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                if (conn.getResponseCode() != 200) {
                    logger.warning("Nodo no responde 200 en /nodeStatus: " + endpoint);
                    conn.disconnect();
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
                    logger.warning("Formato JSON inesperado de /nodeStatus en: " + endpoint);
                    continue;
                }
                int bs = Integer.parseInt(mBS.group(1));
                long cap = Long.parseLong(mCap.group(1));
                if (bs != expectedBlockSize) {
                    logger.warning("blockSize no coincide en " + endpoint + ": " + bs);
                    continue;
                }
                if (cap != expectedCapacity) {
                    logger.warning("capacityBytes no coincide en " + endpoint + ": " + cap);
                    continue;
                }

                good.add(endpoint);
            } catch (IOException e) {
                logger.warning("Error contactando a nodo " + endpoint + ": " + e.getMessage());
            }
        }

        availableNodes.clear();
        availableNodes.addAll(good);
        logger.info("Nodos disponibles: " + availableNodes);
    }

    /**
     * @return lista inmutable de endpoints que están activos y válidos
     */
    public Set<String> getAvailableNodes() {
        return Collections.unmodifiableSet(availableNodes);
    }
}

