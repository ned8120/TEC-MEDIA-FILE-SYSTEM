package com.tecmfs.controller;

import com.tecmfs.common.models.Block;
import com.tecmfs.common.models.Stripe;
import com.tecmfs.common.util.ParityCalculator;
import com.tecmfs.controller.config.ControllerConfig;
import com.tecmfs.controller.models.StoredFile;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Divide archivos en bloques, calcula paridad RAID5 y distribuye bloques entre Disk Nodes.
 * También reconstruye archivos completos leyendo bloques de los Disk Nodes.
 */
public class FileDistributor {
    private static final Logger logger = Logger.getLogger(FileDistributor.class.getName());
    private final MetadataManager metadataManager;
    private final ControllerConfig config;
    private final int blockSize;
    private final List<String> nodeEndpoints;

    public FileDistributor(MetadataManager metadataManager, ControllerConfig config) {
        this.metadataManager = metadataManager;
        this.config = config;
        this.blockSize = config.getBlockSize();
        this.nodeEndpoints = config.getDiskNodeEndpoints();
    }

    /**
     * Distribuye un archivo: particiona, calcula paridad y envía bloques.
     */
    public String distribute(String fileName, InputStream in) throws IOException {
        String fileId = UUID.randomUUID().toString();
        List<byte[]> dataBlocks = new ArrayList<>();
        try (BufferedInputStream bis = new BufferedInputStream(in)) {
            byte[] buf = new byte[blockSize]; int r;
            while ((r = bis.read(buf)) != -1) {
                if (r < blockSize) {
                    byte[] p = new byte[r]; System.arraycopy(buf,0,p,0,r);
                    dataBlocks.add(p);
                } else {
                    dataBlocks.add(buf.clone());
                }
            }
        }
        int n = nodeEndpoints.size(), dataCount = n-1;
        int stripes = (int)Math.ceil((double)dataBlocks.size()/dataCount);
        List<Stripe> list = new ArrayList<>(); int idx=0;
        for(int s=0;s<stripes;s++){
            List<byte[]> slice = new ArrayList<>();
            for(int i=0;i<dataCount;i++){
                if(idx<dataBlocks.size()) slice.add(dataBlocks.get(idx++));
                else slice.add(new byte[blockSize]);
            }
            for (int i = 0; i < slice.size(); i++) {
                byte[] b = slice.get(i);
                if (b.length != blockSize) {
                    byte[] padded = new byte[blockSize];
                    System.arraycopy(b, 0, padded, 0, b.length);
                    slice.set(i, padded);
                }
            }
            byte[] par = ParityCalculator.calculateParity(slice);
            Stripe stripe=new Stripe(fileId+"_s"+s,fileId,s);
            int parityPos=s%n, di=0;
            for(int pos=0;pos<n;pos++){
                Block b;
                if(pos==parityPos){ b=new Block(stripe.getStripeId()+"_p", par, Block.BlockType.PARITY); }
                else { b=new Block(stripe.getStripeId()+"_d"+di, slice.get(di), Block.BlockType.DATA); di++; }
                stripe.setBlock(pos,b);
                sendBlock(nodeEndpoints.get(pos),b);
            }
            list.add(stripe);
            logger.info("Stripe " + stripe.getStripeId() + " distribuido.");
        }
        metadataManager.saveStoredFile(new StoredFile(fileId,fileName,list));
        return fileId;
    }

    /**
     * Reconstruye el archivo completo leyendo bloques, recuperando faltantes.
     */
    public InputStream reconstruct(String fileId) throws IOException {
        StoredFile sf = metadataManager.getStoredFile(fileId);
        if (sf == null) throw new FileNotFoundException("StoredFile " + fileId + " no existe");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(Stripe stripe: sf.getStripes()){
            int n=nodeEndpoints.size();
            Block[] blks=new Block[n]; boolean[] ok=new boolean[n];
            int missing=-1;
            for(int i=0;i<n;i++){
                try{ byte[] data=fetchBlock(nodeEndpoints.get(i),stripe.getBlock(i).getBlockId());
                    blks[i]=new Block(stripe.getStripeId()+"_r"+i,data,stripe.getBlock(i).getType()); ok[i]=true;
                }catch(IOException e){ missing=i; }
            }
            if(missing>=0){
                stripe.setBlock(missing,stripe.reconstructBlock(missing));
                blks[missing]=stripe.getBlock(missing);
            }
            for(int i=0;i<n;i++){
                if(stripe.getBlock(i).getType()==Block.BlockType.DATA){ baos.write(blks[i].getData()); }
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Envía un bloque al Disk Node.
     */
    public void sendBlock(String endpoint, Block block) throws IOException {
        URL url=new URL(endpoint+"/storeBlock?blockId="+block.getBlockId());
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setDoOutput(true); c.setRequestMethod("POST");
        c.getOutputStream().write(block.getData());
        if(c.getResponseCode()!=200) logger.warning("Error " + c.getResponseCode());
        c.disconnect();
    }

    /**
     * Lee un bloque desde el Disk Node.
     */
    private byte[] fetchBlock(String endpoint, String blockId) throws IOException {
        URL url=new URL(endpoint+"/getBlock?blockId="+blockId);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET"); c.connect();
        if(c.getResponseCode()!=200) throw new IOException("HTTP " + c.getResponseCode());
        try(InputStream is=c.getInputStream(); ByteArrayOutputStream baos=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192]; int r;
            while((r=is.read(buf))!=-1) baos.write(buf,0,r);
            return baos.toByteArray();
        }finally{c.disconnect();}
    }
}
