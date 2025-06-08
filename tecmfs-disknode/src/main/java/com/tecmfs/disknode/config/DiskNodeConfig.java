package com.tecmfs.disknode.config;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.SAXException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Lectura y validación de configuración para el Disk Node.
 */
public class DiskNodeConfig {
    private static final Logger logger = Logger.getLogger(DiskNodeConfig.class.getName());

    private final String ip;
    private final int port;
    private final String storagePath;
    private final int blockSize;

    private DiskNodeConfig(String ip, int port, String storagePath, int blockSize) {
        this.ip = ip;
        this.port = port;
        this.storagePath = storagePath;
        this.blockSize = blockSize;
    }

    /**
     * Carga la configuración desde un archivo XML.
     * @param xmlPath ruta al archivo config.xml
     * @throws ParserConfigurationException errores de configuración del parser
     * @throws SAXException errores al parsear XML
     * @throws IOException errores de E/S en el archivo
     */
    public static DiskNodeConfig loadFromFile(String xmlPath)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(xmlPath));
        Element root = doc.getDocumentElement();

        String ip = root.getElementsByTagName("ip").item(0).getTextContent().trim();
        String portStr = root.getElementsByTagName("port").item(0).getTextContent().trim();
        String storage = root.getElementsByTagName("storagePath").item(0).getTextContent().trim();
        String bsStr = root.getElementsByTagName("blockSize").item(0).getTextContent().trim();

        if (ip.isEmpty() || storage.isEmpty()) {
            throw new IllegalArgumentException("IP y StoragePath no pueden estar vacíos.");
        }

        int port = Integer.parseInt(portStr);
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }

        int bs = Integer.parseInt(bsStr);
        if (bs <= 0) {
            throw new IllegalArgumentException("El tamaño del bloque debe ser positivo.");
        }

        DiskNodeConfig cfg = new DiskNodeConfig(ip, port, storage, bs);
        logger.info("Configuración cargada: IP=" + ip + ", Puerto=" + port + ", StoragePath=" + storage + ", BlockSize=" + bs);
        return cfg;
    }

    // Getters
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getStoragePath() { return storagePath; }
    public int getBlockSize() { return blockSize; }
}

