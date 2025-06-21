package com.tecmfs.disknode.config;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.SAXException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
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
    private final long capacityBytes;  // Capacidad total del nodo en bytes


    public DiskNodeConfig(String ip, int port, String storagePath, int blockSize, long capacityBytes) {
        this.ip = ip;
        this.port = port;
        this.storagePath = Paths.get(storagePath).toAbsolutePath().toString();
        this.blockSize = blockSize;
        this.capacityBytes = capacityBytes;
    }




    public static DiskNodeConfig loadFromFile(String xmlPath)
            throws ParserConfigurationException, SAXException, IOException {
        List<DiskNodeConfig> configs = loadAllFromFile(xmlPath);
        if (configs.isEmpty()) throw new IllegalArgumentException("No se encontró ninguna configuración válida.");
        return configs.get(0);
    }

    /**
     * Carga todas las configuraciones de nodos desde un único XML.
     */

    public static List<DiskNodeConfig> loadAllFromFile(String xmlPath)
            throws ParserConfigurationException, SAXException, IOException {
        List<DiskNodeConfig> configList = new ArrayList<>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(xmlPath));
        Element root = doc.getDocumentElement();

        NodeList nodeList = root.getElementsByTagName("diskNode");
        System.out.println("Cantidad de nodos leídos: " + nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            System.out.println("Nodo #" + i + " tipo: " + n.getNodeType() + ", nombre: " + n.getNodeName());
            Element node = (Element) n;

            String ip = getTagValue(node, "ip");
            String portStr = getTagValue(node, "port");
            String storage = getTagValue(node, "storagePath");
            String bsStr = getTagValue(node, "blockSize");
            String capStr = getTagValue(node, "capacityBytes");
            System.out.println("Debug Nodo #" + i + ": ip=[" + ip + "] port=[" + portStr + "] storage=[" + storage + "] blockSize=[" + bsStr + "]" + "]capacityBytes=[" + capStr + "]");

            if (ip.isEmpty() || portStr.isEmpty() || storage.isEmpty() || bsStr.isEmpty() || capStr.isEmpty()) {
                logger.warning("Nodo omitido: algún campo está vacío (ip, port, storagePath, blockSize o capacityBytes).");
                continue;
            }

            int port, bs;
            long cap;
            try {
                port = Integer.parseInt(portStr);
                bs = Integer.parseInt(bsStr);
                cap = Long.parseLong(capStr);
            } catch (NumberFormatException e) {
                logger.warning("Nodo omitido: formato numérico inválido. " + e.getMessage());
                continue;
            }

            if (port <= 0 || port > 65535) {
                logger.warning("Nodo omitido: puerto debe estar entre 1 y 65535.");
                continue;
            }
            if (bs <= 0) {
                logger.warning("Nodo omitido: blockSize debe ser positivo.");
                continue;
            }
            if (cap <= 0) {
                logger.warning("Nodo omitido: totalSize debe ser positivo.");
                continue;
            }
            if (cap < bs) {
                logger.warning("Nodo omitido: totalSize debe ser al menos blockSize.");
                continue;
            }

            configList.add(new DiskNodeConfig(ip, port, storage, bs, cap));
            logger.info(String.format(
                    "Nodo cargado: %s:%d → %s [blockSize=%d, capacityBytes=%d]",
                    ip, port, storage, bs, cap
            ));
        }
        return configList;
    }

    private static String getTagValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() == 0 || list.item(0) == null) return "";
        return list.item(0).getTextContent().trim();
    }


    // Getters
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getStoragePath() { return storagePath; }
    public int getBlockSize() { return blockSize; }
    public long getCapacityBytes() { return capacityBytes; }
}
