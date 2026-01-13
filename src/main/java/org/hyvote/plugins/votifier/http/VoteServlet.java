package org.hyvote.plugins.votifier.http;

import com.hypixel.hytale.server.core.HytaleServer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.crypto.CryptoUtil;
import org.hyvote.plugins.votifier.crypto.VoteDecryptionException;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;
import org.hyvote.plugins.votifier.vote.VoteParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;

/**
 * HTTP endpoint for receiving encrypted vote notifications.
 *
 * <p>Endpoint: POST /Hyvote/HytaleVotifier/vote</p>
 *
 * <p>Accepts RSA-encrypted vote payloads from voting sites. The request body
 * should contain Base64-encoded encrypted data using the server's public key
 * (available via the /status endpoint).</p>
 *
 * <p>Response codes:</p>
 * <ul>
 *   <li>200 OK - Vote received and decrypted successfully</li>
 *   <li>400 Bad Request - Empty payload, invalid Base64, or decryption failed</li>
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
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        String payload = body.toString().trim();

        // Validate payload is not empty
        if (payload.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Empty payload");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: empty payload");
            return;
        }

        // Decode Base64 payload
        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid Base64 encoding");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: invalid Base64 encoding");
            return;
        }

        // Attempt decryption
        byte[] decryptedBytes;
        try {
            decryptedBytes = CryptoUtil.decrypt(encryptedBytes, plugin.getKeyManager().getPrivateKey());
        } catch (VoteDecryptionException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote payload");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: decryption failed - %s", e.getMessage());
            return;
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process vote request");
            return;
        }

        // Parse decrypted vote data
        Vote vote;
        try {
            vote = VoteParser.parse(decryptedBytes);
        } catch (VoteParseException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote format");
            plugin.getLogger().at(Level.WARNING).log("Rejected vote request: parse failed - %s", e.getMessage());
            return;
        }

        // Fire vote event for other plugins to handle rewards
        VoteEvent voteEvent = new VoteEvent(plugin, vote);
        HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received vote request from %s", req.getRemoteAddr());
            plugin.getLogger().at(Level.INFO).log("Parsed vote: service=%s, username=%s, address=%s",
                    vote.serviceName(), vote.username(), vote.address());
            plugin.getLogger().at(Level.INFO).log("Fired VoteEvent for %s from service %s",
                    vote.username(), vote.serviceName());
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(String.format(
                "{\"status\": \"ok\", \"message\": \"Vote processed for %s\"}", vote.username()));
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().println(String.format("{\"status\": \"error\", \"message\": \"%s\"}", message));
    }
}
