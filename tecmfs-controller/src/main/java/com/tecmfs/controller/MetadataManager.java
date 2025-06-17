package com.tecmfs.controller;

import com.tecmfs.controller.models.StoredFile;
import com.tecmfs.controller.models.NodeStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Gestiona los metadatos de archivos y estados de Disk Nodes.
 */
public class MetadataManager {
    private static final Logger logger = Logger.getLogger(MetadataManager.class.getName());

    // Mapa fileId -> StoredFile
    private final ConcurrentMap<String, StoredFile> filesMap = new ConcurrentHashMap<>();
    // Mapa nodeId -> NodeStatus
    private final ConcurrentMap<String, NodeStatus> nodeStatusMap = new ConcurrentHashMap<>();

    /**
     * Registra un nuevo archivo en el sistema.
     */
    public void saveStoredFile(StoredFile storedFile) {
        filesMap.put(storedFile.getFileId(), storedFile);
        logger.info("StoredFile registrado: " + storedFile.getFileId());
    }


    /**
     * Recupera un StoredFile por su ID.
     * (Actualmente no se usa, pero puede ser útil para descargar/reconstruir)
     */
    public StoredFile getStoredFile(String fileId) {
        return filesMap.get(fileId);
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
        // Llamamos al método real de NodeStatus
        status.updateStatus(alive);
        logger.info("NodeStatus actualizado: " + nodeId + " -> " + alive);
    }

    /**
     * Obtiene el estado de todos los Disk Nodes.
     */
    public void removeFile(String fileId) {
        filesMap.remove(fileId);
        logger.info("StoredFile eliminado: " + fileId);
    }
    public List<NodeStatus> getAllNodeStatus() {
        return new ArrayList<>(nodeStatusMap.values());
    }
}
