package com.tecmfs.common.models;
/**
 * Representa un bloque individual de datos o paridad en el sistema RAID 5
 * Cada bloque tiene un tamaño fijo y puede contener datos del archivo original o información de paridad
 */

public class Block {
    private String blockId;           // Identificador único del bloque
    private byte[] data;             // Datos del bloque (pueden ser datos del archivo o paridad)
    private BlockType type;          // Tipo de bloque: DATA o PARITY
    private int size;                // Tamaño del bloque en bytes
    private String checksum;         // Checksum para verificar integridad
    private boolean isCorrupted;     // Indica si el bloque está corrupto

    // Enum para tipos de bloque
    public enum BlockType {
        DATA,    // Bloque contiene datos del archivo original
        PARITY   // Bloque contiene información de paridad
    }

    // Constructor vacío
    public Block() {
        this.isCorrupted = false;
    }

    // Constructor completo
    public Block(String blockId, byte[] data, BlockType type) {
        this.blockId = blockId;
        this.data = data != null ? data.clone() : null;
        this.type = type;
        this.size = data != null ? data.length : 0;
        this.isCorrupted = false;
        this.checksum = calculateChecksum();
    }

    // Constructor para crear bloque vacío con tamaño específico
    public Block(String blockId, int size, BlockType type) {
        this.blockId = blockId;
        this.data = new byte[size];
        this.type = type;
        this.size = size;
        this.isCorrupted = false;
        this.checksum = calculateChecksum();
    }

    /**
     * Calcula un checksum simple para verificar integridad del bloque
     * @return checksum como string hexadecimal
     */
    private String calculateChecksum() {
        if (data == null) return "0";

        long sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return Long.toHexString(sum);
    }

    /**
     * Verifica si el bloque está íntegro comparando checksums
     * @return true si el bloque no está corrupto
     */
    public boolean verifyIntegrity() {
        String currentChecksum = calculateChecksum();
        boolean isIntact = currentChecksum.equals(this.checksum);
        this.isCorrupted = !isIntact;
        return isIntact;
    }

    /**
     * Actualiza los datos del bloque y recalcula el checksum
     * @param newData nuevos datos para el bloque
     */
    public void updateData(byte[] newData) {
        if (newData != null && newData.length <= this.size) {
            this.data = newData.clone();
            this.checksum = calculateChecksum();
            this.isCorrupted = false;
        } else {
            throw new IllegalArgumentException("Los datos exceden el tamaño máximo del bloque");
        }
    }

    /**
     * Limpia los datos del bloque (lo llena de ceros)
     */
    public void clear() {
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                data[i] = 0;
            }
            this.checksum = calculateChecksum();
        }
    }

    // Getters y Setters
    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public byte[] getData() {
        return data != null ? data.clone() : null;
    }

    public void setData(byte[] data) {
        updateData(data);
    }

    public BlockType getType() {
        return type;
    }

    public void setType(BlockType type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
        if (data == null || data.length != size) {
            this.data = new byte[size];
            this.checksum = calculateChecksum();
        }
    }

    public String getChecksum() {
        return checksum;
    }

    public boolean isCorrupted() {
        return isCorrupted;
    }

    public void setCorrupted(boolean corrupted) {
        this.isCorrupted = corrupted;
    }

    @Override
    public String toString() {
        return String.format("Block{id='%s', type=%s, size=%d, corrupted=%s}",
                blockId, type, size, isCorrupted);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Block block = (Block) obj;
        return blockId != null ? blockId.equals(block.blockId) : block.blockId == null;
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }
}