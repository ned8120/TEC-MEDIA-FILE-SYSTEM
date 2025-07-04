package com.tecmfs.controller;

import com.tecmfs.controller.models.NodeStatus;
import com.tecmfs.controller.models.StoredFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Gestiona los metadatos de archivos y estados de Disk Nodes.
 * Incluye almacenamiento de información detallada obtenida de cada nodo.
 */
public class MetadataManager {
    private static final Logger logger = Logger.getLogger(MetadataManager.class.getName());

    // Mapa fileId -> StoredFile
    private final ConcurrentMap<String, StoredFile> filesMap = new ConcurrentHashMap<>();
    // Mapa nodeId -> NodeStatus
    private final ConcurrentMap<String, NodeStatus> nodeStatusMap = new ConcurrentHashMap<>();
    // Mapa nodeId -> JSON detallado (/detailedNodeStatus)
    private final ConcurrentMap<String, String> detailedStatusMap = new ConcurrentHashMap<>();

    /**
     * Registra un nuevo archivo en el sistema.
     */
    public void saveStoredFile(StoredFile storedFile) {
        filesMap.put(storedFile.getFileId(), storedFile);
        logger.info("StoredFile registrado: " + storedFile.getFileId());
    }

    /**
     * Recupera un StoredFile por su ID.
     */
    public StoredFile getStoredFile(String fileId) {
        return filesMap.get(fileId);
    }

    /**
     * Elimina un StoredFile por su ID.
     */
    public void removeFile(String fileId) {
        if (filesMap.remove(fileId) != null) {
            logger.info("StoredFile eliminado: " + fileId);
        }
    }

    /**
     * Obtiene todos los archivos registrados.
     */
    public List<StoredFile> getAllStoredFiles() {
        return new ArrayList<>(filesMap.values());
    }

    /**
     * Actualiza el estado de un Disk Node.
     * Crea una nueva entrada si no existía.
     */
    public void updateNodeStatus(String nodeId, boolean alive) {
        NodeStatus status = nodeStatusMap.computeIfAbsent(
                nodeId, id -> new NodeStatus(id, alive)
        );
        status.updateStatus(alive);
        logger.info("NodeStatus actualizado: " + nodeId + " -> " + alive);
    }

    /**
     * Obtiene el estado de todos los Disk Nodes.
     */
    public List<NodeStatus> getAllNodeStatus() {
        return new ArrayList<>(nodeStatusMap.values());
    }

    /**
     * Almacena o actualiza la información detallada JSON para un Disk Node.
     * Se espera que provenga del endpoint /detailedNodeStatus.
     */
    public void updateDetailedNodeStatus(String nodeId, String detailedJson) {
        detailedStatusMap.put(nodeId, detailedJson);
        logger.fine("Detailed status actualizado para: " + nodeId);
    }

    /**
     * Retorna un map inmutable con el JSON detallado de cada nodo.
     */
    public Map<String, String> getAllDetailedNodeStatus() {
        return Map.copyOf(detailedStatusMap);
    }
}
