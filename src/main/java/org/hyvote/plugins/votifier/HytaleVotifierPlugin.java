package org.hyvote.plugins.votifier;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import org.hyvote.plugins.votifier.command.TestVoteCommand;
import org.hyvote.plugins.votifier.crypto.RSAKeyManager;
import org.hyvote.plugins.votifier.http.StatusServlet;
import org.hyvote.plugins.votifier.http.TestVoteServlet;
import org.hyvote.plugins.votifier.http.VoteServlet;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * HytaleVotifier - A Votifier-style plugin for Hytale that receives vote notifications
 * from voting websites via HTTP and fires events for other plugins to handle rewards.
 */
public class HytaleVotifierPlugin extends JavaPlugin {

    private VotifierConfig config;
    private RSAKeyManager keyManager;
    private WebServerPlugin webServerPlugin;

    public HytaleVotifierPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("HytaleVotifier enabling...");
        loadConfig();
        initializeKeys();
        initializeWebServer();
        registerCommands();
        getLogger().at(Level.INFO).log("HytaleVotifier enabled - debug=%s, keyPath=%s", config.debug(), config.keyPath());
    }

    @Override
    protected void shutdown() {
        if (webServerPlugin != null) {
            webServerPlugin.removeServlets(this);
            getLogger().at(Level.INFO).log("Unregistered HTTP endpoints");
        }
        getLogger().at(Level.INFO).log("HytaleVotifier disabled");
    }

    /**
     * Returns the plugin configuration.
     *
     * @return the current configuration
     */
    public VotifierConfig getConfig() {
        return config;
    }

    /**
     * Returns the RSA key manager.
     *
     * @return the RSA key manager
     */
    public RSAKeyManager getKeyManager() {
        return keyManager;
    }

    private void loadConfig() {
        getLogger().at(Level.INFO).log("Loading configuration...");
        this.config = VotifierConfig.defaults();
    }

    private void initializeKeys() {
        this.keyManager = new RSAKeyManager();
        Path keyDirectory = getDataDirectory().resolve(config.keyPath());

        try {
            if (keyManager.keysExist(keyDirectory)) {
                keyManager.loadKeyPair(keyDirectory);
                getLogger().at(Level.INFO).log("Loaded existing RSA keys from %s", keyDirectory);
            } else {
                getLogger().at(Level.INFO).log("No RSA keys found, generating new 2048-bit key pair...");
                keyManager.generateKeyPair();
                keyManager.saveKeyPair(keyDirectory);
                getLogger().at(Level.INFO).log("Generated new RSA key pair and saved to %s", keyDirectory);
            }
        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to initialize RSA keys: %s", e.getMessage());
            throw new RuntimeException("Failed to initialize RSA keys", e);
        }
    }

    private void initializeWebServer() {
        var plugin = PluginManager.get().getPlugin(new PluginIdentifier("Nitrado", "WebServer"));
        if (!(plugin instanceof WebServerPlugin webServer)) {
            getLogger().at(Level.SEVERE).log("WebServer plugin not found - HTTP endpoints disabled");
            return;
        }
        this.webServerPlugin = webServer;
        registerServlets();
    }

    private void registerServlets() {
        if (webServerPlugin == null) {
            return;
        }
        try {
            webServerPlugin.addServlet(this, "/vote", new VoteServlet(this));
            webServerPlugin.addServlet(this, "/status", new StatusServlet(this));
            webServerPlugin.addServlet(this, "/test", new TestVoteServlet(this));
            getLogger().at(Level.INFO).log("Registered HTTP endpoints at /Hyvote/HytaleVotifier/vote, /status, and /test");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register HTTP endpoints: %s", e.getMessage());
        }
    }

    private void registerCommands() {
        TestVoteCommand testVoteCommand = new TestVoteCommand(this);
        getCommandRegistry().registerCommand(testVoteCommand);
        getLogger().at(Level.INFO).log("Registered /testvote command");
    }
}
