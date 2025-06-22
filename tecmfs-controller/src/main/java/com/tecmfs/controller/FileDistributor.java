package com.tecmfs.controller;

import com.tecmfs.common.models.Block;
import com.tecmfs.common.models.Stripe;
import com.tecmfs.common.util.ParityCalculator;
import com.tecmfs.controller.config.ControllerConfig;
import com.tecmfs.controller.models.StoredFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Divide archivos en bloques, calcula paridad RAID5 y distribuye bloques entre nodos activos.
 * También reconstruye archivos completos leyendo bloques desde nodos activos.
 * <p>
 * Validación de nodos: RAID5 típico utiliza 4 nodos, pero en modo degradado
 * permite operar con 2 o 3 nodos con advertencia.
 */
public class FileDistributor {
    private static final Logger logger = Logger.getLogger(FileDistributor.class.getName());

    private final MetadataManager metadataManager;
    private final ControllerConfig config;
    private final NodeMonitor nodeMonitor;
    private final int blockSize;

    public FileDistributor(MetadataManager metadataManager,
                           ControllerConfig config,
                           NodeMonitor nodeMonitor) {
        this.metadataManager = metadataManager;
        this.config = config;
        this.nodeMonitor = nodeMonitor;
        this.blockSize = config.getBlockSize();
    }

    /**
     * Distribuye un archivo: particiona, calcula paridad y envía bloques a nodos activos.
     * @param fileName nombre original
     * @param in stream de datos del archivo
     * @return fileId generado
     * @throws IOException si hay fallo I/O o nodos insuficientes (<2)
     */
    public String distribute(String fileName, InputStream in) throws IOException {
        // 1. Creamos ID único
        String fileId = UUID.randomUUID().toString();

        // 2. Leemos todo en bloques de tamaño fijo
        List<byte[]> dataBlocks = new ArrayList<>();
        try (BufferedInputStream bis = new BufferedInputStream(in)) {
            byte[] buf = new byte[blockSize];
            int read;
            while ((read = bis.read(buf)) != -1) {
                byte[] chunk = (read == blockSize) ? buf.clone() : Arrays.copyOf(buf, read);
                dataBlocks.add(chunk);
            }
        }

        // 3. Obtenemos nodos activos
        // Obtener nodos activos manteniendo el orden definido en config
        List<String> activeNodes = config.getDiskNodeEndpoints().stream()
                .filter(nodeMonitor.getAvailableNodes()::contains)
                .collect(Collectors.toList());
        int n = activeNodes.size();
        // Validación de nodos para RAID5
        if (n < 2) {
            throw new IllegalStateException("Se requieren al menos 2 nodos activos, encontrados: " + n);
        }
        if (n < 4) {
            logger.warning("Solo " + n + " nodos activos; RAID5 típico requiere 4 nodos. Operando en modo degradado.");
        }
        int dataCount = n - 1;

        // 4. Calculamos número de stripes
        int stripes = (int) Math.ceil((double) dataBlocks.size() / dataCount);
        List<Stripe> stripeList = new ArrayList<>();
        int idx = 0;

        for (int s = 0; s < stripes; s++) {
            // 4.1 recolectamos dataCount bloques (pad con ceros si hace falta)
            List<byte[]> slice = new ArrayList<>();
            for (int i = 0; i < dataCount; i++) {
                if (idx < dataBlocks.size()) {
                    slice.add(dataBlocks.get(idx++));
                } else {
                    slice.add(new byte[blockSize]);
                }
            }
            // 4.2 aseguramos tamaño uniforme
            for (int i = 0; i < slice.size(); i++) {
                byte[] b = slice.get(i);
                if (b.length != blockSize) {
                    slice.set(i, Arrays.copyOf(b, blockSize));
                }
            }
            // 4.3 calculamos paridad
            byte[] parity = ParityCalculator.calculateParity(slice);

            // 4.4 creamos Stripe y asignamos bloques en round-robin
            Stripe stripe = new Stripe(fileId + "_stripe" + s, fileId, s);
            int parityPos = s % n;
            int dataIdx = 0;
            for (int pos = 0; pos < n; pos++) {
                Block blk;
                String destino = activeNodes.get(pos);
                if (pos == parityPos) {
                    blk = new Block(stripe.getStripeId() + "_p", parity, Block.BlockType.PARITY);
                    logger.info(" Creado bloque de PARIDAD: " + blk.getBlockId() + " → Nodo: " + destino);
                } else {
                    blk = new Block(stripe.getStripeId() + "_d" + dataIdx, slice.get(dataIdx), Block.BlockType.DATA);
                    logger.info(" Creado bloque de DATOS: " + blk.getBlockId() + " → Nodo: " + destino);
                    dataIdx++;
                }
                stripe.setBlock(pos, blk);
                sendBlock(destino, blk);
            }
            stripeList.add(stripe);
            logger.info("Stripe " + stripe.getStripeId() + " distribuido");
        }

        // 5. Guardamos metadatos
        metadataManager.saveStoredFile(new StoredFile(fileId, fileName, stripeList));
        return fileId;
    }

    /**
     * Reconstruye el archivo completo leyendo y recuperando bloques en nodos activos.
     */
    public InputStream reconstruct(String fileId) throws IOException {
        StoredFile sf = metadataManager.getStoredFile(fileId);
        if (sf == null) {
            throw new FileNotFoundException("StoredFile " + fileId + " no existe");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<String> activeNodes = new ArrayList<>(nodeMonitor.getAvailableNodes());
        int n = activeNodes.size();

        for (Stripe stripe : sf.getStripes()) {
            // recolectamos bloques
            byte[][] blocks = new byte[n][];
            boolean[] available = new boolean[n];
            List<Integer> missingPositions = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String nodo = activeNodes.get(i);
                String bloqueId = stripe.getBlock(i).getBlockId();
                try {
                    blocks[i] = fetchBlock(nodo, bloqueId);
                    available[i] = true;
                    logger.info(" Bloque disponible: " + bloqueId + " desde nodo: " + nodo);
                } catch (IOException e) {
                    missingPositions.add(i);
                    available[i] = false;
                    logger.warning(" Bloque perdido: " + bloqueId + " en nodo: " + nodo);
                }
            }

            // Verificamos cuántos bloques faltan
            if (missingPositions.size() > 1) {
                logger.warning(" No se puede reconstruir stripe " + stripe.getStripeId()
                        + ": múltiples bloques perdidos → " + missingPositions);
                continue;
            }

            if (missingPositions.size() == 1) {
                int missing = missingPositions.get(0);
                Block recovered = stripe.reconstructBlock(missing);
                blocks[missing] = recovered.getData();
                logger.info("🛠 Reconstruyendo bloque faltante: " + recovered.getBlockId() + " usando paridad");
                sendBlock(activeNodes.get(missing), recovered);
                logger.info(" Bloque reconstruido enviado a nodo: " + activeNodes.get(missing));
            }

            // escribimos datos (ignoramos paridad)
            for (int i = 0; i < n; i++) {
                Block b = stripe.getBlock(i);
                if (b.getType() == Block.BlockType.DATA) {
                    baos.write(blocks[i]);
                    logger.info(" Escribiendo bloque de datos: " + b.getBlockId());
                }
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Envía un bloque a un Disk Node.
     */
    private void sendBlock(String endpoint, Block block) throws IOException {
        URL url = new URL(endpoint + "/storeBlock?blockId=" + block.getBlockId());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(block.getData());
        }
        if (conn.getResponseCode() != 200) {
            logger.warning("Error " + conn.getResponseCode() + " al enviar bloque a " + endpoint);
        }
        conn.disconnect();
    }

    /**
     * Descarga un bloque de un Disk Node.
     */
    private byte[] fetchBlock(String endpoint, String blockId) throws IOException {
        URL url = new URL(endpoint + "/getBlock?blockId=" + blockId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP " + conn.getResponseCode());
        }
        try (InputStream is = conn.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int r;
            while ((r = is.read(b)) != -1) buf.write(b, 0, r);
            return buf.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}