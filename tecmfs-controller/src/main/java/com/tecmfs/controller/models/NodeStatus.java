package com.tecmfs.controller.models;

import java.time.Instant;

/**
 * Representa el estado y métricas de un Disk Node en el sistema.
 */
public class NodeStatus {
    private final String nodeId;
    private boolean active;
    private Instant lastResponseTime;
    private int storedBlockCount;

    /**
     * @param nodeId identificador único (por ejemplo, URL base)
     * @param active estado inicial del nodo
     */
    public NodeStatus(String nodeId, boolean active) {
        this.nodeId = nodeId;
        updateStatus(active);
        this.storedBlockCount = 0;
    }

    /**
     * Actualiza el estado (activo o inactivo) y registra el tiempo de respuesta.
     */
    public void updateStatus(boolean isActive) {
        this.active = isActive;
        registerResponse();
    }

    /**
     * Registra la última vez que el nodo respondió (ahora).
     */
    public void registerResponse() {
        this.lastResponseTime = Instant.now();
    }

    /**
     * @return identificador del nodo
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * @return true si el nodo está activo
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @return timestamp de la última respuesta recibida
     */
    public Instant getLastResponseTime() {
        return lastResponseTime;
    }

    /**
     * @return número de bloques almacenados (si se conoce)
     */
    public int getStoredBlockCount() {
        return storedBlockCount;
    }

    /**
     * Establece la cantidad de bloques que el nodo reporta tener.
     */
    public void setStoredBlockCount(int count) {
        this.storedBlockCount = count;
    }

    @Override
    public String toString() {
        return String.format("NodeStatus{id=%s, active=%s, lastResponse=%s, blocks=%d}",
                nodeId, active, lastResponseTime, storedBlockCount);
    }
}
