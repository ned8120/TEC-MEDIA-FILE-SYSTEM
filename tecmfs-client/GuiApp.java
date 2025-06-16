//package com.tecmfs.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class GuiApp extends JFrame {
    private JTextArea fileListArea;
    private JTextField fileIdField;

    public GuiApp() {
        setTitle("TEC Media File System");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Área central para mostrar archivos
        fileListArea = new JTextArea();
        fileListArea.setEditable(false);
        add(new JScrollPane(fileListArea), BorderLayout.CENTER);

        // Botonera inferior
        JPanel bottomPanel = new JPanel();

        JButton uploadBtn = new JButton("Subir PDF");
        JButton downloadBtn = new JButton("Descargar PDF");
        JButton deleteBtn = new JButton("Eliminar PDF");

        fileIdField = new JTextField(15);
        bottomPanel.add(new JLabel("ID del archivo:"));
        bottomPanel.add(fileIdField);
        bottomPanel.add(uploadBtn);
        bottomPanel.add(downloadBtn);
        bottomPanel.add(deleteBtn);

        add(bottomPanel, BorderLayout.SOUTH);

        uploadBtn.addActionListener(e -> uploadFile());
        downloadBtn.addActionListener(e -> downloadFile());
        deleteBtn.addActionListener(e -> deleteFile());

        // Simulación de lista de archivos
        listFiles();

        setVisible(true);
    }

    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                URL url = new URL("http://localhost:8080/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");

                try (OutputStream os = conn.getOutputStream();
                     InputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }

                int response = conn.getResponseCode();
                if (response == 200) {
                    JOptionPane.showMessageDialog(this, "Archivo subido correctamente.");
                } else {
                    JOptionPane.showMessageDialog(this, "Error al subir archivo: " + response);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        }
    }

    private void downloadFile() {
        String id = fileIdField.getText();
        if (id.isBlank()) return;

        try {
            URL url = new URL("http://localhost:8080/download?fileId=" + id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                File outFile = new File("descarga_" + id + ".pdf");
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int r;
                    while ((r = is.read(buffer)) != -1) fos.write(buffer, 0, r);
                }
                JOptionPane.showMessageDialog(this, "Descarga completada: " + outFile.getName());
            } else {
                JOptionPane.showMessageDialog(this, "Archivo no encontrado.");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void deleteFile() {
        String id = fileIdField.getText();
        if (id.isBlank()) return;

        try {
            URL url = new URL("http://localhost:8080/delete?fileId=" + id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            int res = conn.getResponseCode();
            if (res == 200) {
                JOptionPane.showMessageDialog(this, "Archivo eliminado.");
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo eliminar: " + res);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void listFiles() {
        // Esto lo puedes reemplazar por una petición a un endpoint /list real
        fileListArea.setText("Archivo1.pdf (id: abc123)\nArchivo2.pdf (id: def456)\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiApp::new);
    }
}
