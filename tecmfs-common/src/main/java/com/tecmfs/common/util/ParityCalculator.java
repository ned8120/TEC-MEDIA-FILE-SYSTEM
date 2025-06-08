package com.tecmfs.common.util;
import java.util.List;

/**
 * Utilidad para cálculo de paridad XOR en bloques de datos.
 */
public class ParityCalculator {

    /**
     * Calcula el bloque de paridad a partir de una lista de bloques de datos.
     *
     * @param dataBlocks listas de bytes de cada bloque de datos
     * @return arreglo de bytes resultante de la operación XOR
     */
    public static byte[] calculateParity(List<byte[]> dataBlocks) {
        if (dataBlocks == null || dataBlocks.isEmpty()) {
            throw new IllegalArgumentException("Debe proveer al menos un bloque de datos.");
        }

        int length = dataBlocks.get(0).length;
        byte[] parity = new byte[length];

        for (byte[] block : dataBlocks) {
            if (block.length != length) {
                throw new IllegalArgumentException("Todos los bloques deben tener el mismo tamaño.");
            }
            for (int i = 0; i < length; i++) {
                parity[i] ^= block[i];
            }
        }
        return parity;
    }
}
