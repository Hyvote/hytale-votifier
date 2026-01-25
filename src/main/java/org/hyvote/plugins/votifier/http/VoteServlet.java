package org.hyvote.plugins.votifier.http;

import com.hypixel.hytale.server.core.HytaleServer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.crypto.CryptoUtil;
import org.hyvote.plugins.votifier.crypto.VoteDecryptionException;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.util.BroadcastUtil;
import org.hyvote.plugins.votifier.util.RewardCommandUtil;
import org.hyvote.plugins.votifier.util.VoteNotificationUtil;
import org.hyvote.plugins.votifier.vote.ProtocolDetector;
import org.hyvote.plugins.votifier.vote.ProtocolDetector.Protocol;
import org.hyvote.plugins.votifier.vote.V2SignatureException;
import org.hyvote.plugins.votifier.vote.V2VoteParser;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;
import org.hyvote.plugins.votifier.vote.VoteParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;

/**
 * HTTP endpoint for receiving vote notifications.
 *
 * <p>Endpoint: POST /Hyvote/HytaleVotifier/vote</p>
 *
 * <p>Supports both Votifier V1 (RSA encryption) and V2 (HMAC-SHA256) protocols.
 * The protocol is auto-detected based on the payload format.</p>
 *
 * <p>V1 Protocol: Base64-encoded RSA-encrypted vote data</p>
 * <p>V2 Protocol: JSON with "payload" and "signature" fields</p>
 *
 * <p>Response codes:</p>
 * <ul>
 *   <li>200 OK - Vote received and processed successfully</li>
 *   <li>400 Bad Request - Empty payload, invalid format, or parse error</li>
 *   <li>401 Unauthorized - V2 signature verification failed</li>
 *   <li>500 Internal Server Error - Unexpected server error</li>
 * </ul>
 */
public class VoteServlet extends HttpServlet {

    private final HytaleVotifierPlugin plugin;

    public VoteServlet(HytaleVotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        // Read request body
        String payload = readRequestBody(req);

        // Validate payload is not empty
        if (payload.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Empty payload");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: empty payload");
            return;
        }

        // Detect protocol
        Protocol protocol = ProtocolDetector.detect(payload);

        if (protocol == Protocol.UNKNOWN) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unable to detect vote protocol");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: unknown protocol");
            return;
        }

        // Process vote based on detected protocol
        Vote vote;
        try {
            vote = switch (protocol) {
                case V2_JSON -> processV2Vote(payload);
                case V1_RSA -> processV1Vote(payload);
                case UNKNOWN -> throw new VoteParseException("Unable to detect vote protocol");
            };
        } catch (VoteParseException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote format");
            plugin.getLogger().at(Level.WARNING).log("Rejected %s vote request: %s", protocol, e.getMessage());
            return;
        } catch (V2SignatureException e) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Signature verification failed");
            plugin.getLogger().at(Level.WARNING).log("Rejected V2 vote: %s", e.getMessage());
            return;
        } catch (VoteDecryptionException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote payload");
            plugin.getLogger().at(Level.WARNING).log("Rejected V1 vote: decryption failed - %s", e.getMessage());
            return;
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process vote request");
            return;
        }

        // Process the vote (fire event, notifications, rewards)
        processVote(vote);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received %s vote from %s: service=%s, username=%s",
                    protocol, req.getRemoteAddr(), vote.serviceName(), vote.username());
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(String.format(
                "{\"status\": \"ok\", \"message\": \"Vote processed for %s\"}", vote.username()));
    }

    /**
     * Reads the request body as a trimmed string.
     */
    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString().trim();
    }

    /**
     * Processes a V1 (RSA-encrypted) vote payload.
     */
    private Vote processV1Vote(String payload) throws VoteDecryptionException, VoteParseException {
        // Decode Base64 payload
        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new VoteParseException("Invalid Base64 encoding", e);
        }

        // Decrypt with RSA private key
        byte[] decryptedBytes = CryptoUtil.decrypt(encryptedBytes, plugin.getKeyManager().getPrivateKey());

        // Parse vote data
        return VoteParser.parse(decryptedBytes);
    }

    /**
     * Processes a V2 (HMAC-SHA256 signed) vote payload.
     */
    private Vote processV2Vote(String payload) throws VoteParseException, V2SignatureException {
        return V2VoteParser.parse(payload, plugin.getConfig().voteSites());
    }

    /**
     * Fires vote event and processes rewards/notifications.
     */
    private void processVote(Vote vote) {
        // Fire vote event for other plugins to handle rewards
        VoteEvent voteEvent = new VoteEvent(plugin, vote);
        HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

        // Display toast notification to the player if enabled
        VoteNotificationUtil.displayVoteToast(plugin, vote);

        // Broadcast vote announcement to all online players if enabled
        BroadcastUtil.broadcastVote(plugin, vote);

        // Execute reward commands
        RewardCommandUtil.executeRewardCommands(plugin, vote);
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().println(String.format("{\"status\": \"error\", \"message\": \"%s\"}", message));
    }
}
