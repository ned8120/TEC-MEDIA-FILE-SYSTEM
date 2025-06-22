package com.tecmfs.client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;
//Va estar en la nueva branch
/**
 * Aplicación cliente Swing para visualizar y gestionar el sistema distribuido.
 * Ahora incluye botón para ver estado RAID detallado.
 */
public class GuiApp extends JFrame {
    private JTextArea fileListArea;
    private JTextField fileIdField;
    private JTextField searchField;
    private JCheckBox[] nodeBoxes;
    private JButton startNodesBtn;

    public GuiApp() {
        setTitle("TEC Media File System");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        fileListArea = new JTextArea();
        fileListArea.setEditable(false);
        add(new JScrollPane(fileListArea), BorderLayout.CENTER);

        // Panel de simulación de nodos
        JPanel nodePanel = new JPanel();
        nodePanel.setBorder(BorderFactory.createTitledBorder("Simular caída de nodos"));
        String[] ports = {"8001", "8002", "8003", "8004"};
        nodeBoxes = new JCheckBox[ports.length];
        for (int i = 0; i < ports.length; i++) {
            nodeBoxes[i] = new JCheckBox("Nodo " + ports[i] + " activo", true);
            nodePanel.add(nodeBoxes[i]);
        }
        startNodesBtn = new JButton("Aplicar cambios de estado de nodos");
        startNodesBtn.addActionListener(e -> startNodes());
        nodePanel.add(startNodesBtn);
        add(nodePanel, BorderLayout.NORTH);

        // Panel inferior con controles de archivos y estado RAID
        JPanel bottomPanel = new JPanel();
        fileIdField = new JTextField(15);
        bottomPanel.add(new JLabel("ID del archivo:"));
        bottomPanel.add(fileIdField);

        JButton uploadBtn = new JButton("Subir PDF");
        JButton downloadBtn = new JButton("Descargar PDF");
        JButton deleteBtn = new JButton("Eliminar PDF");
        JButton refreshBtn = new JButton("Actualizar lista");
        JButton searchBtn = new JButton("Buscar por nombre");
        JButton statusBtn = new JButton("Ver estado RAID");  // Nuevo botón

        bottomPanel.add(uploadBtn);
        bottomPanel.add(downloadBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(refreshBtn);
        searchField = new JTextField(10);
        bottomPanel.add(new JLabel("Nombre contiene:"));
        bottomPanel.add(searchField);
        bottomPanel.add(searchBtn);
        bottomPanel.add(statusBtn);  // Añadir al panel

        add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        uploadBtn.addActionListener(e -> uploadFile());
        downloadBtn.addActionListener(e -> downloadFile());
        deleteBtn.addActionListener(e -> deleteFile());
        refreshBtn.addActionListener(e -> listFiles());
        searchBtn.addActionListener(e -> listFilesByName());
        statusBtn.addActionListener(e -> showRaidStatus()); // Acción para ver estado RAID

        listFiles();
        setVisible(true);
    }

    /**
     * Muestra un diálogo con la respuesta del endpoint /detailedClusterStatus.
     */
    private void showRaidStatus() {
        SwingWorker<DefaultTableModel, Void> worker = new SwingWorker<>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                String[] cols = {"Nodo","Bloque ID","Tipo","Tamaño (bytes)","Modificado"};
                DefaultTableModel model = new DefaultTableModel(cols, 0);

                URL url = new URL("http://localhost:7000/detailedClusterStatus");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != 200)
                    throw new IOException("HTTP " + conn.getResponseCode());

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }

                JSONArray nodes = new JSONArray(sb.toString());
                for (int i = 0; i < nodes.length(); i++) {
                    JSONObject node = nodes.getJSONObject(i);
                    String nodeId = node.getString("nodeId");
                    JSONArray details = node.getJSONArray("details");
                    for (int j = 0; j < details.length(); j++) {
                        JSONObject blk = details.getJSONObject(j);
                        String blockId      = blk.getString("blockId");
                        String type         = blk.getString("type");
                        long   size         = blk.getLong("size");
                        long   lm           = blk.getLong("lastModified");
                        String modificado   = new java.util.Date(lm).toString();
                        model.addRow(new Object[]{nodeId, blockId, type, size, modificado});
                    }
                }
                return model;
            }

            @Override
            protected void done() {
                try {
                    JTable table = new JTable(get());
                    table.setAutoCreateRowSorter(true);
                    JScrollPane scroll = new JScrollPane(table);
                    scroll.setPreferredSize(new Dimension(700,400));
                    JOptionPane.showMessageDialog(GuiApp.this, scroll,
                            "Estado RAID Detallado", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GuiApp.this,
                            "Error al obtener estado RAID: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
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

    private void startNodes() {
        for (JCheckBox box : nodeBoxes) {
            String port = box.getText().split(" ")[1];
            String storage = "./storage" + port.charAt(port.length() - 1); // asume ./storage1, ./storage2, etc.

            if (box.isSelected()) {
                reiniciarNodo(port, storage); // Casilla marcada → reiniciar (si estaba apagado)
            } else {
                apagarNodo(port);             // Casilla desmarcada → apagar nodo
            }
        }

        JOptionPane.showMessageDialog(this, "Cambios de estado aplicados a los nodos.");
    }


    private void reiniciarNodo(String puerto, String storagePath) {
        try {
            String rutaAbsoluta = new File("../tecmfs-disknode/target/classes").getCanonicalPath();
            File checkRuta = new File(rutaAbsoluta);
            if (!checkRuta.exists()) {
                JOptionPane.showMessageDialog(this, "No se encontró la ruta del módulo disknode: " + rutaAbsoluta);
                return;
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "java",
                    "-cp", rutaAbsoluta,
                    "com.tecmfs.disknode.server.DiskNodeServer",
                    "--port=" + puerto,
                    "--storage=" + storagePath
            );
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            builder.start();
            System.out.println("Nodo " + puerto + " lanzado con ruta: " + rutaAbsoluta);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error al lanzar nodo " + puerto + ": " + e.getMessage());
        }
    }


    void apagarNodo(String puerto) {

        try {
            URL url = new URL("http://localhost:" + puerto + "/shutdown");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            int response = conn.getResponseCode();
            if (response == 200) {
                System.out.println("Nodo en puerto " + puerto + " fue apagado.");
            } else {
                System.out.println("Fallo al apagar nodo en puerto " + puerto + ": " + response);
            }
        } catch (IOException e) {
            System.out.println("Error al apagar nodo en puerto " + puerto + ": " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiApp::new);
    }

}
