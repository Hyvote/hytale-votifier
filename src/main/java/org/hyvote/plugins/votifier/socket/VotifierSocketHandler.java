package org.hyvote.plugins.votifier.socket;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.HytaleServer;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.util.BroadcastUtil;
import org.hyvote.plugins.votifier.util.RewardCommandUtil;
import org.hyvote.plugins.votifier.util.VoteNotificationUtil;
import org.hyvote.plugins.votifier.vote.V2ChallengeException;
import org.hyvote.plugins.votifier.vote.V2SignatureException;
import org.hyvote.plugins.votifier.vote.V2VoteParser;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;

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
 * Handles a single V2 protocol socket connection.
 *
 * <p>Protocol flow:</p>
 * <ol>
 *   <li>Send greeting with challenge: "VOTIFIER 2 &lt;challenge&gt;\n"</li>
 *   <li>Read V2 binary packet: 0x733A + length + JSON</li>
 *   <li>Parse and validate vote (signature + challenge)</li>
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
                plugin.getLogger().at(Level.WARNING).log("V2 socket connection timed out from %s",
                        socket.getRemoteSocketAddress());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Error handling V2 socket connection: %s", e.getMessage());
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

        // Read V2 packet
        DataInputStream dis = new DataInputStream(socket.getInputStream());

        // Read and verify magic bytes
        int magic = dis.readShort() & 0xFFFF;
        if (magic != V2_MAGIC) {
            sendError(writer, "Invalid protocol magic");
            plugin.getLogger().at(Level.WARNING).log("Invalid V2 magic bytes from %s: 0x%04X",
                    socket.getRemoteSocketAddress(), magic);
            return;
        }

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

    /**
     * V2 protocol response format.
     */
    private record V2Response(String status, String cause, String errorMessage) {}
}
