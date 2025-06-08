package com.tecmfs.common.models;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadatos y control de un archivo dentro del sistema.
 * Incluye mapeo de stripes, gestión de estado e integridad.
 */

public class FileMetadata {
    private final UUID fileId;
    private String fileName;
    private long fileSize;            // Tamaño en bytes
    private int blockSize;            // Tamaño de cada bloque en bytes
    private final int totalBlocks;    // Número total de bloques necesarios

    // Collection de stripes que componen el archivo
    private final List<Stripe> stripes;

    // Estado de integridad del archivo según paridad de cada stripe
    private boolean integrityVerified;

    /**
     * Constructor principal.
     * @param fileName nombre del archivo
     * @param fileSize tamaño del archivo en bytes
     * @param blockSize tamaño de bloque en bytes
     */
    public FileMetadata(String fileName, long fileSize, int blockSize) {
        this.fileId = UUID.randomUUID();
        this.fileName = Objects.requireNonNull(fileName, "fileName no puede ser nulo");
        this.fileSize = Math.max(0, fileSize);
        this.blockSize = Math.max(1, blockSize);
        this.totalBlocks = (int) Math.ceil((double) this.fileSize / this.blockSize);
        this.stripes = new ArrayList<>();
        this.integrityVerified = false;
    }

    /**
     * Añade un stripe al archivo.
     * @param stripe stripe a agregar
     */
    public void addStripe(Stripe stripe) {
        Objects.requireNonNull(stripe, "stripe no puede ser nulo");
        if (!stripe.getFileId().equals(this.fileId)) {
            throw new IllegalArgumentException("El stripe pertenece a otro archivo");
        }
        stripes.add(stripe);
        integrityVerified = false;
    }

    /**
     * Obtiene los stripes ordenados por índice.
     */
    public List<Stripe> getStripes() {
        synchronized (stripes) {
            stripes.sort((a, b) -> Integer.compare(a.getStripeIndex(), b.getStripeIndex()));
            return Collections.unmodifiableList(new ArrayList<>(stripes));
        }
    }

    /**
     * Verifica la integridad de todo el archivo comprobando cada stripe.
     * @return true si todos los stripes pasan la verificación
     */
    public boolean verifyIntegrity() {
        synchronized (stripes) {
            if (stripes.isEmpty()) {
                integrityVerified = false;
                return false;
            }
            for (Stripe stripe : stripes) {
                if (!stripe.verifyIntegrity()) {
                    integrityVerified = false;
                    return false;
                }
            }
            integrityVerified = true;
            return true;
        }
    }

    // Getters y setters básicos
    public UUID getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = Objects.requireNonNull(fileName);
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = Math.max(0, fileSize);
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize debe ser mayor que cero");
        }
        this.blockSize = blockSize;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    /**
     * Indica si la última verificación de integridad fue exitosa.
     */
    public boolean isIntegrityVerified() {
        return integrityVerified;
    }


    @Override
    public String toString() {
        return String.format("FileMetadata{id=%s, name='%s', size=%d bytes, blocks=%d, stripes=%d, integrity=%s}",
                fileId, fileName, fileSize, totalBlocks, stripes.size(), integrityVerified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMetadata)) return false;
        FileMetadata that = (FileMetadata) o;
        return fileId.equals(that.fileId);
    }

    @Override
    public int hashCode() {
        return fileId.hashCode();
    }
}

