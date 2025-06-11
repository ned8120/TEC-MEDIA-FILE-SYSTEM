package com.tecmfs.controller.models;

import com.tecmfs.common.models.Block;
import com.tecmfs.common.models.Stripe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa la información de un archivo almacenado de forma distribuida.
 * Incluye el mapeo de stripes y bloques en los Disk Nodes.
 */
public class StoredFile {
    private final String fileId;
    private final String fileName;
    private final List<Stripe> stripes;

    /**
     * @param fileId   identificador único del archivo
     * @param fileName nombre original del archivo
     * @param stripes  lista de stripes que componen el archivo
     */
    public StoredFile(String fileId, String fileName, List<Stripe> stripes) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.stripes = new ArrayList<>(stripes);
    }

    /**
     * Añade un stripe al final de la lista.
     */
    public void addStripe(Stripe stripe) {
        stripes.add(stripe);
    }

    /**
     * Devuelve una vista inmutable de los stripes.
     */
    public List<Stripe> getStripes() {
        return Collections.unmodifiableList(stripes);
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * Localiza la posición (índice de nodo) del bloque indicado.
     * @param blockId identificador del bloque
     * @return índice de nodo donde se almacenó el bloque, o -1 si no se encontró
     */
    public int getBlockLocation(String blockId) {
        for (Stripe stripe : stripes) {
            for (int pos = 0; pos < stripe.getTotalBlocks(); pos++) {
                Block b = stripe.getBlock(pos);
                if (b != null && blockId.equals(b.getBlockId())) {
                    return pos;
                }
            }
        }
        return -1;
    }

    /**
     * Verifica la integridad de todos los stripes del archivo.
     * @return true si todos los stripes pasan la comprobación de paridad
     */
    public boolean verifyIntegrity() {
        for (Stripe stripe : stripes) {
            if (!stripe.verifyIntegrity()) {
                return false;
            }
        }
        return true;
    }
}
