package com.gmw.gcts.analyzer.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.eclipse.jface.preference.IPreferenceStore;

import com.gmw.gcts.analyzer.Activator;
import com.gmw.gcts.analyzer.model.AnalysisResult;
import com.gmw.gcts.analyzer.preferences.PreferenceConstants;

/**
 * Calls the ABAP ICF service GET /sap/bc/zgcts/analyze?tr=<TR>
 * and returns a parsed AnalysisResult.
 *
 * Connection settings (URL, user, password, timeout) come from the
 * Eclipse preference store — configured via Window → Preferences → gCTS Tools.
 */
public final class AnalyzerHttpClient {

    private static final String ICF_PATH = "/sap/bc/zgcts/analyze";

    private final HttpClient httpClient;
    private final String     systemUrl;
    private final String     authHeader;
    private final int        timeoutSeconds;

    // ── Constructor — reads preferences ──────────────────────────────────────

    public AnalyzerHttpClient() {
        IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();

        this.systemUrl     = normalise(prefs.getString(PreferenceConstants.PREF_SYSTEM_URL));
        this.timeoutSeconds = prefs.getInt(PreferenceConstants.PREF_TIMEOUT_S);
        this.authHeader    = buildAuthHeader(
                prefs.getString(PreferenceConstants.PREF_USERNAME),
                loadPassword());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the dependency analysis for the given TR number.
     *
     * @param tr validated TR number, e.g. "GMWK900691"
     * @return AnalysisResult — never null; check hasError() for failure details
     */
    public AnalysisResult analyze(String tr) {
        if (systemUrl.isEmpty()) {
            return AnalysisResult.error(
                "No SAP system URL configured.\n" +
                "Go to Window → Preferences → gCTS Tools → Dependency Analyzer.");
        }

        try {
            String encodedTr = URLEncoder.encode(tr, StandardCharsets.UTF_8);
            URI    uri        = URI.create(systemUrl + ICF_PATH + "?tr=" + encodedTr);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", authHeader)
                    .header("Accept", "application/json")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401) {
                return AnalysisResult.error(
                    "HTTP 401 Unauthorized — check username/password in preferences.");
            }
            if (response.statusCode() == 404) {
                return AnalysisResult.error(
                    "HTTP 404 — ICF service not found.\n" +
                    "Activate /sap/bc/zgcts/analyze in SICF on your SAP system.");
            }
            if (response.statusCode() != 200) {
                return AnalysisResult.error(
                    "HTTP " + response.statusCode() + " from SAP system:\n" +
                    response.body());
            }

            return AnalysisResult.fromJson(response.body());

        } catch (java.net.ConnectException e) {
            return AnalysisResult.error(
                "Cannot reach " + systemUrl + "\n" +
                "Check system URL in preferences and network connectivity.");
        } catch (Exception e) {
            return AnalysisResult.error("Connection error: " + e.getMessage());
        }
    }

    /**
     * Quick connectivity check — returns null on success, error message on failure.
     */
    public String testConnection() {
        AnalysisResult probe = analyze("TESTK000000");
        // A 400 "invalid TR format" from the server still means the service is reachable
        if (probe.hasError()) {
            String msg = probe.errorMessage;
            if (msg.contains("Invalid TR format") || msg.contains("Missing query")) {
                return null; // service is up — TR validation error means we reached it
            }
            return msg;
        }
        return null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String normalise(String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static String buildAuthHeader(String user, String password) {
        if (user == null || user.isBlank()) return "";
        String credentials = user + ":" + (password != null ? password : "");
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String loadPassword() {
        try {
            // Eclipse Secure Storage — keeps password off plain preference store
            org.eclipse.equinox.security.storage.ISecurePreferences root =
                org.eclipse.equinox.security.storage.SecurePreferencesFactory.getDefault();
            org.eclipse.equinox.security.storage.ISecurePreferences node =
                root.node("com.gmw.gcts.analyzer");
            return node.get(PreferenceConstants.PREF_PASSWORD, "");
        } catch (Exception e) {
            // Secure storage unavailable — fall back to plain (empty) password
            return "";
        }
    }
}
