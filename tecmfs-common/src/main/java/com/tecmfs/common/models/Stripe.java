package com.tecmfs.common.models;
/**
 * Representa un stripe en RAID 5 - un conjunto de bloques distribuidos entre los nodos
 * incluyendo bloques de datos y un bloque de paridad
 * En RAID 5 con 4 nodos: 3 bloques de datos + 1 bloque de paridad
 */
public class Stripe {
    private String stripeId;                    // Identificador único del stripe
    private Block[] blocks;                     // Array de bloques que conforman el stripe
    private int parityPosition;                 // Posición del bloque de paridad (0-3)
    private int dataBlockCount;                 // Número de bloques de datos (3 en RAID 5)
    private int totalBlocks;                    // Total de bloques incluyendo paridad (4)
    private boolean isComplete;                 // Indica si el stripe tiene todos sus bloques
    private String fileId;                      // ID del archivo al que pertenece este stripe
    private int stripeIndex;                    // Índice del stripe dentro del archivo

    public static final int RAID5_TOTAL_BLOCKS = 4;  // 4 nodos en total
    public static final int RAID5_DATA_BLOCKS = 3;   // 3 bloques de datos

    // Constructor
    public Stripe(String stripeId, String fileId, int stripeIndex) {
        this.stripeId = stripeId;
        this.fileId = fileId;
        this.stripeIndex = stripeIndex;
        this.totalBlocks = RAID5_TOTAL_BLOCKS;
        this.dataBlockCount = RAID5_DATA_BLOCKS;
        this.blocks = new Block[totalBlocks];
        this.isComplete = false;

        // Calcular posición de paridad rotando para balancear carga
        // En RAID 5 la paridad se distribuye entre todos los discos
        this.parityPosition = stripeIndex % totalBlocks;
    }

    /**
     * Agrega un bloque al stripe en la posición especificada
     *
     * @param position posición del nodo (0-3)
     * @param block    bloque a agregar
     */
    public void setBlock(int position, Block block) {
        if (position < 0 || position >= totalBlocks) {
            throw new IllegalArgumentException("Posición inválida: " + position);
        }

        this.blocks[position] = block;
        checkCompletion();
    }

    /**
     * Obtiene el bloque en la posición especificada
     *
     * @param position posición del nodo (0-3)
     * @return bloque en esa posición o null si no existe
     */
    public Block getBlock(int position) {
        if (position < 0 || position >= totalBlocks) {
            return null;
        }
        return blocks[position];
    }

    /**
     * Obtiene todos los bloques de datos (excluyendo paridad)
     *
     * @return array con los bloques de datos
     */
    public Block[] getDataBlocks() {
        Block[] dataBlocks = new Block[dataBlockCount];
        int dataIndex = 0;

        for (int i = 0; i < totalBlocks; i++) {
            if (i != parityPosition && blocks[i] != null) {
                dataBlocks[dataIndex++] = blocks[i];
            }
        }

        return dataBlocks;
    }

    /**
     * Obtiene el bloque de paridad
     *
     * @return bloque de paridad o null si no existe
     */
    public Block getParityBlock() {
        return blocks[parityPosition];
    }

    /**
     * Calcula la paridad XOR de todos los bloques de datos
     *
     * @return bloque de paridad calculado
     */
    public Block calculateParity() {
        // Verificar que tenemos todos los bloques de datos
        Block[] dataBlocks = getDataBlocks();
        int validDataBlocks = 0;

        for (Block block : dataBlocks) {
            if (block != null && !block.isCorrupted()) {
                validDataBlocks++;
            }
        }

        if (validDataBlocks < dataBlockCount) {
            throw new IllegalStateException("No hay suficientes bloques de datos válidos para calcular paridad");
        }

        // Obtener el tamaño del bloque del primer bloque válido
        int blockSize = 0;
        for (Block block : dataBlocks) {
            if (block != null) {
                blockSize = block.getSize();
                break;
            }
        }

        if (blockSize == 0) {
            throw new IllegalStateException("No se pudo determinar el tamaño del bloque");
        }

        // Calcular XOR de todos los bloques de datos
        byte[] parityData = new byte[blockSize];

        for (Block block : dataBlocks) {
            if (block != null && block.getData() != null) {
                byte[] blockData = block.getData();
                for (int i = 0; i < Math.min(blockSize, blockData.length); i++) {
                    parityData[i] ^= blockData[i];
                }
            }
        }

        // Crear bloque de paridad
        String parityBlockId = stripeId + "_parity";
        return new Block(parityBlockId, parityData, Block.BlockType.PARITY);
    }

    /**
     * Reconstruye un bloque perdido usando la paridad
     *
     * @param missingPosition posición del bloque perdido
     * @return bloque reconstruido
     */
    public Block reconstructBlock(int missingPosition) {
        if (missingPosition < 0 || missingPosition >= totalBlocks) {
            throw new IllegalArgumentException("Posición inválida: " + missingPosition);
        }

        // Verificar que tenemos suficientes bloques para reconstruir
        int availableBlocks = 0;
        for (int i = 0; i < totalBlocks; i++) {
            if (i != missingPosition && blocks[i] != null && !blocks[i].isCorrupted()) {
                availableBlocks++;
            }
        }

        if (availableBlocks < (totalBlocks - 1)) {
            throw new IllegalStateException("No hay suficientes bloques válidos para reconstruir");
        }

        // Determinar tamaño del bloque
        int blockSize = 0;
        for (int i = 0; i < totalBlocks; i++) {
            if (i != missingPosition && blocks[i] != null) {
                blockSize = blocks[i].getSize();
                break;
            }
        }

        // Reconstruir usando XOR de todos los bloques disponibles
        byte[] reconstructedData = new byte[blockSize];

        for (int i = 0; i < totalBlocks; i++) {
            if (i != missingPosition && blocks[i] != null && blocks[i].getData() != null) {
                byte[] blockData = blocks[i].getData();
                for (int j = 0; j < Math.min(blockSize, blockData.length); j++) {
                    reconstructedData[j] ^= blockData[j];
                }
            }
        }

        // Determinar tipo del bloque reconstruido
        Block.BlockType blockType = (missingPosition == parityPosition) ?
                Block.BlockType.PARITY : Block.BlockType.DATA;

        String blockId = stripeId + "_block_" + missingPosition;
        return new Block(blockId, reconstructedData, blockType);
    }

    /**
     * Verifica si el stripe está completo (tiene todos los bloques)
     */
    private void checkCompletion() {
        isComplete = true;
        for (Block block : blocks) {
            if (block == null) {
                isComplete = false;
                break;
            }
        }
    }

    /**
     * Verifica la integridad del stripe usando paridad
     *
     * @return true si la integridad es correcta
     */
    public boolean verifyIntegrity() {
        if (!isComplete) {
            return false;
        }

        try {
            Block calculatedParity = calculateParity();
            Block storedParity = getParityBlock();

            if (storedParity == null || calculatedParity == null) {
                return false;
            }

            byte[] calculated = calculatedParity.getData();
            byte[] stored = storedParity.getData();

            if (calculated.length != stored.length) {
                return false;
            }

            for (int i = 0; i < calculated.length; i++) {
                if (calculated[i] != stored[i]) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Getters y Setters
    public String getStripeId() {
        return stripeId;
    }

    public void setStripeId(String stripeId) {
        this.stripeId = stripeId;
    }

    public Block[] getBlocks() {
        return blocks.clone();
    }

    public int getParityPosition() {
        return parityPosition;
    }

    public int getDataBlockCount() {
        return dataBlockCount;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public int getStripeIndex() {
        return stripeIndex;
    }

    public void setStripeIndex(int stripeIndex) {
        this.stripeIndex = stripeIndex;
        // Recalcular posición de paridad
        this.parityPosition = stripeIndex % totalBlocks;
    }

    @Override
    public String toString() {
        return String.format("Stripe{id='%s', fileId='%s', index=%d, parityPos=%d, complete=%s}",
                stripeId, fileId, stripeIndex, parityPosition, isComplete);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Stripe stripe = (Stripe) obj;
        return stripeId != null ? stripeId.equals(stripe.stripeId) : stripe.stripeId == null;
    }

    @Override
    public int hashCode() {
        return stripeId != null ? stripeId.hashCode() : 0;
    }
}