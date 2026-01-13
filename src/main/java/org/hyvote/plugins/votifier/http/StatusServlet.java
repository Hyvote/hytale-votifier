package org.hyvote.plugins.votifier.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.logging.Level;

/**
 * HTTP endpoint for server status and public key retrieval.
 *
 * <p>Endpoint: GET /Hyvote/HytaleVotifier/status</p>
 *
 * <p>Returns JSON with server status and Base64-encoded X509 DER public key
 * for voting sites to use when encrypting vote payloads.</p>
 *
 * <p>Response format:</p>
 * <pre>
 * {
 *   "status": "ok",
 *   "version": "1.0.0",
 *   "serverType": "HytaleVotifier",
 *   "publicKey": "&lt;base64-x509-der&gt;"
 * }
 * </pre>
 */
public class StatusServlet extends HttpServlet {

    private final HytaleVotifierPlugin plugin;

    public StatusServlet(HytaleVotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        try {
            PublicKey publicKey = plugin.getKeyManager().getPublicKey();

            // If keys not initialized, return 503 Service Unavailable
            if (publicKey == null) {
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.getWriter().println("{\"status\": \"error\", \"message\": \"RSA keys not initialized\"}");
                return;
            }

            // Build JSON response
            String json = String.format(
                    "{\"status\": \"ok\", \"version\": \"1.0.0\", \"serverType\": \"HytaleVotifier\"}"
            );

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(json);

        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process status request");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("{\"status\": \"error\", \"message\": \"Internal server error\"}");
        }
    }
}
