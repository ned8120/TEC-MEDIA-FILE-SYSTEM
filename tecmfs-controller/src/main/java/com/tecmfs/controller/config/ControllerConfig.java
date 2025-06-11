package com.tecmfs.controller.config;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.SAXException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Carga y valida la configuración del Controller Node desde un archivo XML.
 */
public class ControllerConfig {
    private static final Logger logger = Logger.getLogger(ControllerConfig.class.getName());

    private final int port;
    private final int blockSize;
    private final int monitorInterval;
    private final List<String> diskNodeEndpoints;

    private ControllerConfig(int port, int blockSize, int monitorInterval, List<String> diskNodeEndpoints) {
        this.port = port;
        this.blockSize = blockSize;
        this.monitorInterval = monitorInterval;
        this.diskNodeEndpoints = diskNodeEndpoints;
    }

    /**
     * Parsea la configuración desde un XML.
     * El XML debe tener la estructura:
     * <controller>
     *   <port>...</port>
     *   <blockSize>...</blockSize>
     *   <monitorInterval>...</monitorInterval>
     *   <diskNodes>
     *     <node>http://...</node>
     *     ...
     *   </diskNodes>
     * </controller>
     *
     * @param xmlPath ruta al archivo config.xml
     * @return instancia de ControllerConfig
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static ControllerConfig loadFromFile(String xmlPath)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(xmlPath));
        Element root = doc.getDocumentElement();

        String portText = getTagValue(root, "port");
        String bsText = getTagValue(root, "blockSize");
        String miText = getTagValue(root, "monitorInterval");

        int port = Integer.parseInt(portText.trim());
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port debe estar entre 1 y 65535");
        }

        int blockSize = Integer.parseInt(bsText.trim());
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize debe ser positivo");
        }

        int monitorInterval = Integer.parseInt(miText.trim());
        if (monitorInterval <= 0) {
            throw new IllegalArgumentException("monitorInterval debe ser positivo");
        }

        // Leer endpoints de diskNodes
        List<String> endpoints = new ArrayList<>();
        NodeList nodesList = root.getElementsByTagName("diskNodes");
        if (nodesList.getLength() > 0) {
            Element diskNodesElem = (Element) nodesList.item(0);
            NodeList nodeElems = diskNodesElem.getElementsByTagName("node");
            for (int i = 0; i < nodeElems.getLength(); i++) {
                String url = nodeElems.item(i).getTextContent().trim();
                if (url.isEmpty()) {
                    throw new IllegalArgumentException("diskNodes.node no puede estar vacío");
                }
                endpoints.add(url);
            }
        }
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos un disk node en <diskNodes>");
        }

        ControllerConfig cfg = new ControllerConfig(port, blockSize, monitorInterval, endpoints);
        logger.info(String.format("ControllerConfig cargado: port=%d, blockSize=%d, monitorInterval=%d, nodes=%s",
                cfg.port, cfg.blockSize, cfg.monitorInterval, cfg.diskNodeEndpoints));
        return cfg;
    }

    private static String getTagValue(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() == 0) {
            throw new IllegalArgumentException(tagName + " no encontrado en config.xml");
        }
        return nl.item(0).getTextContent();
    }

    public int getPort() {
        return port;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getMonitorInterval() {
        return monitorInterval;
    }

    public List<String> getDiskNodeEndpoints() {
        return List.copyOf(diskNodeEndpoints);
    }
}
