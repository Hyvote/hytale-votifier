package org.hyvote.plugins.votifier.socket;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.HytaleServer;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.ProtocolConfig;
import org.hyvote.plugins.votifier.crypto.CryptoUtil;
import org.hyvote.plugins.votifier.crypto.VoteDecryptionException;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.util.BroadcastUtil;
import org.hyvote.plugins.votifier.util.RewardCommandUtil;
import org.hyvote.plugins.votifier.util.VoteNotificationUtil;
import org.hyvote.plugins.votifier.vote.V2ChallengeException;
import org.hyvote.plugins.votifier.vote.V2SignatureException;
import org.hyvote.plugins.votifier.vote.V2VoteParser;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;
import org.hyvote.plugins.votifier.vote.VoteParser;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;

/**
 * Handles a single socket connection for both V1 and V2 protocols.
 *
 * <p>Protocol detection:</p>
 * <ul>
 *   <li>V2 is detected by magic bytes 0x733A at the start of the payload</li>
 *   <li>V1 is assumed for any other payload (256 bytes of RSA-encrypted data)</li>
 * </ul>
 *
 * <p>V2 Protocol flow:</p>
 * <ol>
 *   <li>Send greeting with challenge: "VOTIFIER 2 &lt;challenge&gt;\n"</li>
 *   <li>Read V2 binary packet: 0x733A + length + JSON</li>
 *   <li>Parse and validate vote (signature + challenge)</li>
 *   <li>Send JSON response</li>
 * </ol>
 *
 * <p>V1 Protocol flow:</p>
 * <ol>
 *   <li>Send greeting with challenge: "VOTIFIER 2 &lt;challenge&gt;\n"</li>
 *   <li>Read 256 bytes of RSA-encrypted vote data</li>
 *   <li>Decrypt with RSA private key and parse vote</li>
 *   <li>Send JSON response</li>
 * </ol>
 */
public class VotifierSocketHandler implements Runnable {

    /**
     * V2 protocol magic bytes (0x733A in big-endian).
     */
    private static final int V2_MAGIC = 0x733A;

    /**
     * Maximum message length (64KB).
     */
    private static final int MAX_MESSAGE_LENGTH = 65536;

    /**
     * Socket timeout in milliseconds (30 seconds).
     */
    private static final int SOCKET_TIMEOUT_MS = 30000;

    /**
     * Challenge length in bytes (before Base64 encoding).
     */
    private static final int CHALLENGE_BYTES = 24;

    /**
     * V1 RSA-encrypted payload size (256 bytes for 2048-bit RSA key).
     */
    private static final int V1_RSA_PAYLOAD_SIZE = 256;

    private static final Gson GSON = new Gson();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HytaleVotifierPlugin plugin;
    private final Socket socket;

    /**
     * Creates a new socket handler.
     *
     * @param plugin the plugin instance
     * @param socket the client socket
     */
    public VotifierSocketHandler(HytaleVotifierPlugin plugin, Socket socket) {
        this.plugin = plugin;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            handleConnection();
        } catch (SocketTimeoutException e) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.WARNING).log("Socket connection timed out from %s",
                        socket.getRemoteSocketAddress());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Error handling socket connection: %s", e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private void handleConnection() throws IOException {
        // Generate challenge
        String challenge = generateChallenge();

        // Send greeting
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("VOTIFIER 2 " + challenge + "\n");
        writer.flush();

        // Read first 2 bytes to detect protocol
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        byte[] firstTwoBytes = new byte[2];
        dis.readFully(firstTwoBytes);
        int magic = ((firstTwoBytes[0] & 0xFF) << 8) | (firstTwoBytes[1] & 0xFF);

        if (magic == V2_MAGIC) {
            // V2 protocol detected
            handleV2Connection(dis, writer, challenge);
        } else if (firstTwoBytes[0] == 0x16 && firstTwoBytes[1] == 0x03) {
            // TLS ClientHello detected (0x16 = handshake, 0x03 = TLS version prefix)
            // Note: ~0.0015% chance of false positive with random RSA-encrypted data
            sendError(writer, "TLS/SSL not supported - use plain TCP connection");
            plugin.getLogger().at(Level.WARNING).log("TLS handshake rejected from %s: socket server does not support TLS",
                    socket.getRemoteSocketAddress());
        } else {
            // Not V2 magic bytes - treat as V1 RSA-encrypted payload
            handleV1Connection(dis, writer, firstTwoBytes);
        }
    }

    private void handleV1Connection(DataInputStream dis, Writer writer, byte[] firstTwoBytes) throws IOException {
        // Check if V1 protocol is enabled
        ProtocolConfig protocols = plugin.getConfig().protocols();
        if (protocols == null || !Boolean.TRUE.equals(protocols.v1Enabled())) {
            sendError(writer, "V1 protocol is disabled");
            plugin.getLogger().at(Level.WARNING).log("V1 vote rejected from %s: V1 protocol is disabled",
                    socket.getRemoteSocketAddress());
            return;
        }

        // Read remaining bytes (256 - 2 = 254 bytes for standard RSA payload)
        byte[] remainingBytes = new byte[V1_RSA_PAYLOAD_SIZE - 2];
        dis.readFully(remainingBytes);

        // Combine first two bytes with remaining bytes
        byte[] encryptedPayload = new byte[V1_RSA_PAYLOAD_SIZE];
        encryptedPayload[0] = firstTwoBytes[0];
        encryptedPayload[1] = firstTwoBytes[1];
        System.arraycopy(remainingBytes, 0, encryptedPayload, 2, remainingBytes.length);

        // Decrypt and parse V1 vote
        Vote vote;
        try {
            byte[] decryptedData = CryptoUtil.decrypt(encryptedPayload, plugin.getKeyManager().getPrivateKey());
            vote = VoteParser.parse(decryptedData);
        } catch (VoteDecryptionException e) {
            sendError(writer, "Decryption failed");
            plugin.getLogger().at(Level.WARNING).log("V1 decryption error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("V1 raw payload (first 64 bytes hex): %s",
                        bytesToHex(encryptedPayload, 64));
                plugin.getLogger().at(Level.INFO).log("V1 raw payload (as string): %s",
                        new String(encryptedPayload, StandardCharsets.ISO_8859_1).substring(0, Math.min(64, encryptedPayload.length)));
            }
            return;
        } catch (VoteParseException e) {
            sendError(writer, "Invalid vote format: " + e.getMessage());
            plugin.getLogger().at(Level.WARNING).log("V1 parse error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        }

        // Process the vote
        processVote(vote);

        // Send success response
        sendSuccess(writer);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received V1 socket vote from %s: service=%s, username=%s",
                    socket.getRemoteSocketAddress(), vote.serviceName(), vote.username());
        }
    }

    private void handleV2Connection(DataInputStream dis, Writer writer, String challenge) throws IOException {
        // Read message length
        int length = dis.readShort() & 0xFFFF;
        if (length <= 0 || length > MAX_MESSAGE_LENGTH) {
            sendError(writer, "Invalid message length");
            plugin.getLogger().at(Level.WARNING).log("Invalid V2 message length from %s: %d",
                    socket.getRemoteSocketAddress(), length);
            return;
        }

        // Read JSON payload
        byte[] payload = new byte[length];
        dis.readFully(payload);
        String jsonPayload = new String(payload, StandardCharsets.UTF_8);

        // Parse and validate vote
        Vote vote;
        try {
            vote = V2VoteParser.parse(jsonPayload, plugin.getConfig().voteSites(), challenge);
        } catch (VoteParseException e) {
            sendError(writer, "Invalid vote format: " + e.getMessage());
            plugin.getLogger().at(Level.WARNING).log("V2 parse error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        } catch (V2SignatureException e) {
            sendError(writer, "Signature verification failed");
            plugin.getLogger().at(Level.WARNING).log("V2 signature error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        } catch (V2ChallengeException e) {
            sendError(writer, "Challenge verification failed");
            plugin.getLogger().at(Level.WARNING).log("V2 challenge error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        }

        // Process the vote
        processVote(vote);

        // Send success response
        sendSuccess(writer);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received V2 socket vote from %s: service=%s, username=%s",
                    socket.getRemoteSocketAddress(), vote.serviceName(), vote.username());
        }
    }

    private String generateChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void processVote(Vote vote) {
        // Fire vote event for other plugins
        VoteEvent voteEvent = new VoteEvent(plugin, vote);
        HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

        // Display toast notification
        VoteNotificationUtil.displayVoteToast(plugin, vote);

        // Broadcast announcement
        BroadcastUtil.broadcastVote(plugin, vote);

        // Execute reward commands
        RewardCommandUtil.executeRewardCommands(plugin, vote);
    }

    private void sendSuccess(Writer writer) throws IOException {
        V2Response response = new V2Response("ok", null, null);
        writer.write(GSON.toJson(response));
        writer.flush();
    }

    private void sendError(Writer writer, String message) throws IOException {
        V2Response response = new V2Response("error", message, message);
        writer.write(GSON.toJson(response));
        writer.flush();
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    private static String bytesToHex(byte[] bytes, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, maxBytes);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        if (bytes.length > maxBytes) {
            sb.append("...");
        }
        return sb.toString().trim();
    }

    /**
     * V2 protocol response format.
     */
    private record V2Response(String status, String cause, String errorMessage) {}
}
