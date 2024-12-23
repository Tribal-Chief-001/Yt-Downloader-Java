import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

public class YouTubeDownloaderGUI {
    private JTextArea statusArea;
    private JProgressBar progressBar;
    private JButton downloadButton;
    private JButton cancelButton;
    private boolean downloadCancelled = false;

    private static final int MAX_RETRIES = 3;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new YouTubeDownloaderGUI().createAndShowGUI());
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("YouTube Downloader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout(10, 10));

        // Input Panel with GroupLayout
        JPanel inputPanel = new JPanel();
        GroupLayout layout = new GroupLayout(inputPanel);
        inputPanel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel urlLabel = new JLabel("YouTube URL:");
        JTextField urlField = new JTextField(20);
        JLabel formatLabel = new JLabel("Select Format:");
        JComboBox<String> formatDropdown = new JComboBox<>(new String[]{"Video + Audio", "Audio Only"});
        JLabel qualityLabel = new JLabel("Select Quality:");
        JComboBox<String> qualityDropdown = new JComboBox<>(new String[]{"Best", "1080p", "720p", "480p", "360p"});
        JButton selectPathButton = new JButton("Select Download Location");
        JLabel pathLabel = new JLabel("No folder selected");
        downloadButton = new JButton("Download");
        cancelButton = new JButton("Cancel Download");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(downloadButton);
        buttonPanel.add(Box.createHorizontalStrut(5)); // Add some space
        buttonPanel.add(cancelButton);

        // Group components
        layout.setHorizontalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(urlLabel)
                    .addComponent(formatLabel)
                    .addComponent(qualityLabel)
                    .addComponent(selectPathButton)
                    .addComponent(buttonPanel))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(urlField, GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                    .addComponent(formatDropdown)
                    .addComponent(qualityDropdown)
                    .addComponent(pathLabel)
                    .addComponent(progressBar))
        );

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(urlLabel)
                    .addComponent(urlField))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(formatLabel)
                    .addComponent(formatDropdown))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(qualityLabel)
                    .addComponent(qualityDropdown))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(selectPathButton)
                    .addComponent(pathLabel))
                .addComponent(buttonPanel)
                .addComponent(progressBar)
        );

        // Status Area
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setPreferredSize(new Dimension(750, 400));

        // Add components to frame
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Load settings
        final String[] downloadPath = {loadSettings()};
        if (downloadPath[0] != null) {
            pathLabel.setText("Download Location: " + downloadPath[0]);
        }

        // Action listeners
        selectPathButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                downloadPath[0] = chooser.getSelectedFile().getAbsolutePath();
                pathLabel.setText("Download Location: " + downloadPath[0]);
                saveSettings(downloadPath[0]);
            }
        });

        downloadButton.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter a YouTube URL.");
                return;
            }
            if (downloadPath[0] == null) {
                JOptionPane.showMessageDialog(frame, "Please select a download location.");
                return;
            }

            downloadButton.setEnabled(false);
            cancelButton.setEnabled(true);
            progressBar.setValue(0);
            log("Starting download...");

            downloadCancelled = false;
            new Thread(() -> {
                try {
                    checkYtDlp();
                    downloadVideo(url, downloadPath[0], (String) formatDropdown.getSelectedItem(),
                            (String) qualityDropdown.getSelectedItem());
                } catch (IOException | InterruptedException ex) {
                    log("Error: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        downloadButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                        progressBar.setValue(0);
                    });
                }
            }).start();
        });

        cancelButton.addActionListener(e -> {
            downloadCancelled = true;
            log("Download cancellation requested.");
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void checkYtDlp() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--version");
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String version = reader.readLine();
            if (version == null) {
                throw new IOException("yt-dlp is not installed or not found in the system path.");
            }
            log("Using yt-dlp version: " + version);
        }
        if (process.waitFor() != 0) {
            throw new IOException("yt-dlp not found. Please install it first.");
        }
    }

    private void downloadVideo(String url, String outputPath, String format, String quality)
            throws IOException, InterruptedException {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                boolean isPlaylist = url.contains("list=");
                log(isPlaylist ? "Processing playlist: " + url : "Processing video: " + url);

                java.util.List<String> command = new ArrayList<>();  // Fixed ambiguous List reference
                command.add("yt-dlp");

                if (format.equals("Audio Only")) {
                    command.add("-x");
                    command.add("--audio-format");
                    command.add("mp3"); // Can be extended to include other formats
                } else {
                    if (!quality.equals("Best")) {
                        command.add("-f");
                        command.add("bestvideo[height<=" + quality.replace("p", "") + "]+bestaudio/best");
                    } else {
                        command.add("-f");
                        command.add("best");
                    }
                }

                command.add("-o");
                command.add(outputPath + File.separator + "%(title)s.%(ext)s");
                command.add(url);

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (downloadCancelled) {
                        process.destroy();
                        log("Download cancelled by user.");
                        break;
                    }
                    log(line);
                    updateProgress(line);
                }

                if (downloadCancelled) {
                    throw new IOException("Download cancelled.");
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("Download failed with exit code " + exitCode);
                }

                log("Download completed!");
                return;
            } catch (IOException | InterruptedException ex) {
                retries++;
                log("Retrying... (" + retries + "/" + MAX_RETRIES + ")");
                if (retries == MAX_RETRIES) throw ex;
            }
        }
    }
    

    private void updateProgress(String line) {
        if (line.contains("%")) {
            try {
                String percentStr = line.substring(line.indexOf(" ") + 1, line.indexOf("%")).trim();
                int percent = Integer.parseInt(percentStr);
                SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
            } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                // Ignore parsing errors
            }
        }
    }

    private void saveSettings(String downloadPath) {
        Properties properties = new Properties();
        properties.setProperty("downloadPath", downloadPath);
        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            properties.store(out, "User Preferences");
        } catch (IOException ex) {
            log("Error saving settings: " + ex.getMessage());
        }
    }

    private String loadSettings() {
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream("config.properties")) {
            properties.load(in);
            return properties.getProperty("downloadPath", null);
        } catch (IOException ex) {
            log("No saved settings found.");
            return null;
        }
    }
}