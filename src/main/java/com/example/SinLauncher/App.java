// App.java

package com.example.SinLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.example.SinLauncher.SinLauncherEntites.Arch;
import com.example.SinLauncher.SinLauncherEntites.Instance;
import com.example.SinLauncher.SinLauncherEntites.Os;
import com.example.SinLauncher.SinLauncherEntites.Instance.InstanceAlreadyExistsException;
import com.example.SinLauncher.SinLauncherEntites.Instance.InvaildInstanceVersionException;
import com.example.SinLauncher.config.Config;
import com.example.SinLauncher.config.Java;
import com.example.SinLauncher.json.Client;
import com.example.SinLauncher.json.Manifest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;

public class App {
    public static final Logger LOGGER = Logger.getLogger(App.class.getName());
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Client.Argument.class, new Client.ArgumentDeserializer()).create();

    public static final String DIR;
    public static final Path ASSETS_DIR;
    public static final Path LIBRARIES_DIR;
    public static final Path NATIVES_DIR;

    public static final Os OS;
    public static final Arch ARCH;

    public static Config CONFIG;

    static {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            DIR = System.getenv("APPDATA") + "\\SinLauncher";
            OS = Os.Windows;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            DIR = System.getProperty("user.home") + "/.sinlauncher";
            OS = Os.Linux;
        } else {
            DIR = "SinLauncher";
            OS = Os.Linux;
        }

        String arch = System.getProperty("os.arch").toLowerCase();

        if (arch.equals("amd64"))
            ARCH = Arch.X86_64;

        else if (arch.equals("aarch64"))
            ARCH = Arch.Arm64;

        else if (arch.equals("arm"))
            ARCH = Arch.Arm;

        else if (arch.equals("x86"))
            ARCH = Arch.X86;

        else
            ARCH = Arch.X86_64;

        ASSETS_DIR = Paths.get(DIR, "assets");
        LIBRARIES_DIR = Paths.get(DIR, "libraries");
        NATIVES_DIR = Paths.get(DIR, "natives");

        try {
            App.initialize();
            CONFIG = Config.readMainConfig();
        } catch (IOException e) {
            LOGGER.info("Failed to initialize the Launcher");
            System.exit(1);
        }
    }

    static void initializeInstances() throws IOException {
        if (!Files.exists(Instance.PARENT_DIR))
            Files.createDirectories(Instance.PARENT_DIR);

        if (!Files.exists(Instance.INSTANCES_FILE)) {
            Path file = Files.createFile(Instance.INSTANCES_FILE);

            Files.writeString(file, "[]");
        }
    }

    static void initializeLauncherDir() throws IOException {

        if (!Files.exists(Paths.get(DIR)))
            Files.createDirectories(Paths.get(DIR));

        if (!Files.exists(ASSETS_DIR))
            Files.createDirectories(ASSETS_DIR);

        if (!Files.exists(LIBRARIES_DIR))
            Files.createDirectories(LIBRARIES_DIR);

        App.initializeInstances();

        HttpResponse<String> response = Unirest
                .get("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                .asString();

        if (response.getStatus() == 200)
            Files.write(
                    Manifest.PATH,
                    response
                            .getBody()
                            .getBytes());

        else {
            if (!Files.exists(Manifest.PATH))
                throw new IOException("Failed to fetch Manifest.JSON; Response code: " + response.getStatus());
        }
    }

    public static void initialize() throws IOException {
        initializeLauncherDir();

        LOGGER.info("Launcher initialized!");
    }

    public static void intallationManager(String installationName, String version, Java[] cups, int _cups_arg) throws IOException {
        try {
            Instance.createInstance(installationName, version);
    
            Instance createdInstance = Instance.getInstance(installationName);
    
            SwingUtilities.invokeLater(() -> {
                int dialogResult = JOptionPane.showConfirmDialog(null,
                        "Warning: 1GB of data is about to be installed! Do you want to continue?",
                        "Confirm Installation", JOptionPane.YES_NO_OPTION);
    
                if (dialogResult == JOptionPane.YES_OPTION) {
                    try {
                        LOGGER.info("Starting installation for instance: " + installationName);
                        
                        createdInstance.install();
                        
                        LOGGER.info("Installation completed for instance: " + installationName);
                        
                        JOptionPane.showMessageDialog(null,
                                "Installation is complete!",
                                "Installation Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    } 
                    catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Installation error: ", e);
                    }
                } else {
                    JOptionPane
                        .showMessageDialog
                            (
                            null,
                            "Installation has been cancelled!",
                            "Installation Cancelled",
                            JOptionPane.INFORMATION_MESSAGE
                            );
                }
            });
    
        } catch (InstanceAlreadyExistsException e) {
            LOGGER.info("Instance already exists: " + e.getMessage());
    
            SwingUtilities.invokeLater(() -> {
                Instance existingInstance = Instance.getInstance(installationName);
    
                if (existingInstance != null) {
                    try {
                        if (_cups_arg < 0 || _cups_arg >= cups.length) {
                            LOGGER.severe("Invalid index for Java cups: " + _cups_arg);

                            return;
                        }

                        LOGGER.info("Setting Java configuration and launching instance: " + installationName);

                        existingInstance.getConfig().setJava(cups[_cups_arg]);
                        existingInstance.install();
                        existingInstance.launch();

                        LOGGER.info("Instance launched successfully: " + installationName);

                        JOptionPane
                            .showMessageDialog
                                (
                                null,
                                "Instance has been launched successfully!",
                                "Instance launched",
                                JOptionPane.INFORMATION_MESSAGE
                                );

                    } catch (IOException __e) {
                        LOGGER.log(Level.SEVERE, "Launch error: ", __e);
                    }
                } 
                else {
                    LOGGER.warning("Failed to retrieve the instance for launching.");
                }
            });
    
        } catch (InvaildInstanceVersionException e) {
            LOGGER.log(Level.SEVERE, "Invalid instance version: ", e);
        }
    }
    

    public static void launchingManager(String installationName) throws IOException {
        Instance existingInstance = Instance.getInstance(installationName);

        if (existingInstance != null) {
            try {
                existingInstance.launch();
            } catch (IOException ___e) {
                LOGGER.info(___e.getMessage());
            }
        }
    }

    public static void Debugging(String InstanceName) throws IOException {
        System.out.println("Instances: ");

        for (Instance instance : Instance.readInstances())
            System.out.println(instance.toString());

        Instance testInstance = Instance.getInstance(InstanceName);

        Client client = testInstance.readClient();

        System.out.println(GSON.toJson(client));
    }

    public static final Path CURRENT_USER_FILE = Paths.get(App.DIR, "currentUser.txt");

    public static void main(String[] args) {
        try {
            Manifest manifest = Manifest.readManifest();

            System.out.println(CONFIG.getMaxRam());
            System.out.println(manifest.latest.release);

            var cups = Java.getAvailableJavaCups();

            intallationManager("test-912139", "1.6.4", cups, 2);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception: ", e);
        }
    }
}
