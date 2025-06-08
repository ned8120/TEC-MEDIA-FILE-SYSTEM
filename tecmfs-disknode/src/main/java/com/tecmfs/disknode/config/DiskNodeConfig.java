package com.tecmfs.disknode.config;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class DiskNodeConfig {
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

    public static DiskNodeConfig loadFromFile(String xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(xmlPath));
        Element root = doc.getDocumentElement();
        String ip = root.getElementsByTagName("ip").item(0).getTextContent();
        int port = Integer.parseInt(root.getElementsByTagName("port").item(0).getTextContent());
        String storage = root.getElementsByTagName("storagePath").item(0).getTextContent();
        int bs = Integer.parseInt(root.getElementsByTagName("blockSize").item(0).getTextContent());
        return new DiskNodeConfig(ip, port, storage, bs);
    }

    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getStoragePath() { return storagePath; }
    public int getBlockSize() { return blockSize; }
}
