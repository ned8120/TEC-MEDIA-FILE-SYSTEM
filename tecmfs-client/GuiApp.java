package com.tecmfs.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class GuiApp extends JFrame {
    private JTextArea fileListArea;
    private JTextField fileIdField;
    private JTextField searchField; // Para buscar archivos por nombre


    public GuiApp() {
        setTitle("TEC Media File System");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        fileListArea = new JTextArea();
        fileListArea.setEditable(false);
        add(new JScrollPane(fileListArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();

        JButton uploadBtn = new JButton("Subir PDF");
        JButton downloadBtn = new JButton("Descargar PDF");
        JButton deleteBtn = new JButton("Eliminar PDF");
        JButton refreshBtn = new JButton("Actualizar lista");


        fileIdField = new JTextField(15);
        bottomPanel.add(new JLabel("ID del archivo:"));
        bottomPanel.add(fileIdField);
        bottomPanel.add(uploadBtn);
        bottomPanel.add(downloadBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(refreshBtn);
        searchField = new JTextField(10);
        JButton searchBtn = new JButton("Buscar por nombre");

        bottomPanel.add(new JLabel("Nombre contiene:"));
        bottomPanel.add(searchField);
        bottomPanel.add(searchBtn);

        add(bottomPanel, BorderLayout.SOUTH);

        uploadBtn.addActionListener(e -> uploadFile());
        downloadBtn.addActionListener(e -> downloadFile());
        deleteBtn.addActionListener(e -> deleteFile());
        refreshBtn.addActionListener(e -> listFiles());
        searchBtn.addActionListener(e -> listFilesByName());
        listFiles();
        setVisible(true);
    }

    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                String encodedName = URLEncoder.encode(file.getName(), "UTF-8");
                URL url = new URL("http://localhost:7000/uploadFile?fileName=" + encodedName);
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
                    listFiles(); // Actualiza la lista después de subir
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
        String id = fileIdField.getText().trim();
        if (id.isBlank()) return;

        try {
            String encodedId = URLEncoder.encode(id, "UTF-8");
            URL url = new URL("http://localhost:7000/downloadFile?fileId=" + encodedId);
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
                JOptionPane.showMessageDialog(this,
                        " Descarga completada: " + outFile.getName() +
                                "\n Si deseas eliminarlo del sistema distribuido, usa la opción de eliminar.");
            } else {
                JOptionPane.showMessageDialog(this, "Archivo no encontrado.");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void deleteFile() {
        String id = fileIdField.getText().trim();
        if (id.isBlank()) return;

        try {
            String encodedId = URLEncoder.encode(id, "UTF-8");
            URL url = new URL("http://localhost:7000/deleteFile?fileId=" + encodedId);
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
        try {
            URL url = new URL("http://localhost:7000/listFiles");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                // Parseo muy básico, suponiendo JSON como: [{"fileId":"abc","fileName":"x.pdf"},...]
                String json = sb.toString();
                json = json.replace("[", "").replace("]", "");
                String[] items = json.split("\\},\\{");

                StringBuilder output = new StringBuilder();
                for (String item : items) {
                    String cleaned = item.replace("{", "").replace("}", "").replace("\"", "");
                    String[] parts = cleaned.split(",");
                    String fileId = "", fileName = "";
                    for (String p : parts) {
                        if (p.startsWith("fileId:")) fileId = p.substring(7);
                        else if (p.startsWith("fileName:")) fileName = p.substring(9);
                    }
                    output.append("Archivo: ").append(fileName)
                            .append(" (id: ").append(fileId).append(")\n");
                }

                fileListArea.setText(output.toString());
            } else {
                fileListArea.setText("Error al obtener archivos (código " + conn.getResponseCode() + ")");
            }

        } catch (Exception e) {
            fileListArea.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void listFilesByName() {
        String nameFilter = searchField.getText().trim();
        if (nameFilter.isBlank()) {
            listFiles(); // Si está vacío, lista todo
            return;
        }

        try {
            String encoded = URLEncoder.encode(nameFilter, "UTF-8");
            URL url = new URL("http://localhost:7000/listFiles?name=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                String json = sb.toString();
                json = json.replace("[", "").replace("]", "");
                String[] items = json.split("\\},\\{");

                StringBuilder output = new StringBuilder();
                for (String item : items) {
                    String cleaned = item.replace("{", "").replace("}", "").replace("\"", "");
                    String[] parts = cleaned.split(",");
                    String fileId = "", fileName = "";
                    for (String p : parts) {
                        if (p.startsWith("fileId:")) fileId = p.substring(7);
                        else if (p.startsWith("fileName:")) fileName = p.substring(9);
                    }
                    output.append("Archivo: ").append(fileName)
                            .append(" (id: ").append(fileId).append(")\n");
                }

                fileListArea.setText(output.toString());
            } else {
                fileListArea.setText("Error al buscar archivos (código " + conn.getResponseCode() + ")");
            }

        } catch (Exception e) {
            fileListArea.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiApp::new);
    }

}
