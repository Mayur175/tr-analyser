package com.gmw.gcts.analyzer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable model for the JSON response from ZGCTS_ANALYZE_HANDLER.
 * Built via AnalysisResult.fromJson() — no external JSON library needed,
 * parsed with lightweight hand-rolled logic to avoid OSGi dependency issues.
 */
public final class AnalysisResult {

    public static final String RISK_CRITICAL = "CRITICAL";
    public static final String RISK_HIGH     = "HIGH";
    public static final String RISK_MEDIUM   = "MEDIUM";
    public static final String RISK_NONE     = "NONE";

    // ── Summary ──────────────────────────────────────────────────────────────
    public final String tr;
    public final int    taskCount;
    public final int    objectCount;
    public final int    edgeCount;

    // ── Payload ───────────────────────────────────────────────────────────────
    public final List<Cluster>   clusters;
    public final List<PullStep>  pullOrder;

    // ── Error (set when HTTP call failed) ────────────────────────────────────
    public final String errorMessage;

    // ─────────────────────────────────────────────────────────────────────────

    private AnalysisResult(String tr, int taskCount, int objectCount, int edgeCount,
                           List<Cluster> clusters, List<PullStep> pullOrder,
                           String errorMessage) {
        this.tr           = tr;
        this.taskCount    = taskCount;
        this.objectCount  = objectCount;
        this.edgeCount    = edgeCount;
        this.clusters     = Collections.unmodifiableList(clusters);
        this.pullOrder    = Collections.unmodifiableList(pullOrder);
        this.errorMessage = errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    // ── Static factory ───────────────────────────────────────────────────────

    public static AnalysisResult error(String message) {
        return new AnalysisResult("", 0, 0, 0,
                Collections.emptyList(), Collections.emptyList(), message);
    }

    /**
     * Parses the JSON body returned by ZGCTS_ANALYZE_HANDLER.
     * Uses simple string scanning — no Gson/Jackson to keep OSGi deps minimal.
     */
    public static AnalysisResult fromJson(String json) {
        try {
            JsonReader r = new JsonReader(json);

            String tr          = r.stringField("tr");
            int    taskCount   = r.intField("taskCount");
            int    objectCount = r.intField("objectCount");
            int    edgeCount   = r.intField("edgeCount");

            List<Cluster>  clusters  = parseClusters(r);
            List<PullStep> pullOrder = parsePullOrder(r);

            // Check for error field
            String err = r.stringField("error");

            return new AnalysisResult(tr, taskCount, objectCount, edgeCount,
                                      clusters, pullOrder, err);
        } catch (Exception e) {
            return error("JSON parse error: " + e.getMessage());
        }
    }

    private static List<Cluster> parseClusters(JsonReader r) {
        List<Cluster> result = new ArrayList<>();
        String clustersArr = r.arrayContent("clusters");
        if (clustersArr == null) return result;

        for (String obj : JsonReader.splitObjects(clustersArr)) {
            JsonReader cr = new JsonReader(obj);
            String risk  = cr.stringField("risk");
            List<String> tasks = cr.stringArray("tasks");
            List<Edge>   edges = parseEdges(cr);
            result.add(new Cluster(risk, tasks, edges));
        }
        return result;
    }

    private static List<Edge> parseEdges(JsonReader cr) {
        List<Edge> result = new ArrayList<>();
        String edgesArr = cr.arrayContent("edges");
        if (edgesArr == null) return result;

        for (String obj : JsonReader.splitObjects(edgesArr)) {
            JsonReader er = new JsonReader(obj);
            result.add(new Edge(
                er.stringField("from"),
                er.stringField("fromTask"),
                er.stringField("to"),
                er.stringField("toTask"),
                er.stringField("kind"),
                er.stringField("detail")));
        }
        return result;
    }

    private static List<PullStep> parsePullOrder(JsonReader r) {
        List<PullStep> result = new ArrayList<>();
        String arr = r.arrayContent("pullOrder");
        if (arr == null) return result;

        for (String obj : JsonReader.splitObjects(arr)) {
            JsonReader sr = new JsonReader(obj);
            result.add(new PullStep(
                sr.intField("step"),
                sr.stringField("action"),
                sr.stringArray("tasks")));
        }
        return result;
    }

    // ── Nested types ─────────────────────────────────────────────────────────

    public static final class Cluster {
        public final String     risk;
        public final List<String> tasks;
        public final List<Edge>   edges;

        public Cluster(String risk, List<String> tasks, List<Edge> edges) {
            this.risk  = risk != null ? risk : RISK_NONE;
            this.tasks = Collections.unmodifiableList(tasks);
            this.edges = Collections.unmodifiableList(edges);
        }

        public String riskLabel() {
            return switch (risk) {
                case RISK_CRITICAL -> "[CRITICAL] Same object conflict";
                case RISK_HIGH     -> "[HIGH]     Activation dependency";
                case RISK_MEDIUM   -> "[MEDIUM]   Type reference";
                default            -> "[OK]       Independent";
            };
        }

        public String tasksSummary() {
            return String.join(", ", tasks);
        }
    }

    public static final class Edge {
        public final String from;
        public final String fromTask;
        public final String to;
        public final String toTask;
        public final String kind;
        public final String detail;

        public Edge(String from, String fromTask, String to, String toTask,
                    String kind, String detail) {
            this.from     = from;
            this.fromTask = fromTask;
            this.to       = to;
            this.toTask   = toTask;
            this.kind     = kind;
            this.detail   = detail;
        }
    }

    public static final class PullStep {
        public final int          step;
        public final String       action;
        public final List<String> tasks;

        public PullStep(int step, String action, List<String> tasks) {
            this.step   = step;
            this.action = action != null ? action : "ALONE";
            this.tasks  = Collections.unmodifiableList(tasks);
        }

        public String label() {
            String t = String.join(" + ", tasks);
            return switch (action) {
                case "COORDINATE"          -> "Step " + step + ": COORDINATE first, then pull together  →  " + t;
                case "TOGETHER"            -> "Step " + step + ": Pull TOGETHER  →  " + t;
                case "TOGETHER_RECOMMENDED"-> "Step " + step + ": Pull together (recommended)  →  " + t;
                default                    -> "Step " + step + ": Pull alone  →  " + t;
            };
        }
    }

    // ── Minimal JSON reader (no external deps) ────────────────────────────────

    static final class JsonReader {
        private final String src;
        JsonReader(String src) { this.src = src != null ? src : ""; }

        String stringField(String key) {
            String pattern = "\"" + key + "\"";
            int ki = src.indexOf(pattern);
            if (ki < 0) return null;
            int colon = src.indexOf(':', ki + pattern.length());
            if (colon < 0) return null;
            int q1 = src.indexOf('"', colon + 1);
            if (q1 < 0) return null;
            int q2 = src.indexOf('"', q1 + 1);
            while (q2 > 0 && src.charAt(q2 - 1) == '\\') q2 = src.indexOf('"', q2 + 1);
            if (q2 < 0) return null;
            return src.substring(q1 + 1, q2)
                      .replace("\\\"", "\"")
                      .replace("\\n", "\n")
                      .replace("\\\\", "\\");
        }

        int intField(String key) {
            String pattern = "\"" + key + "\"";
            int ki = src.indexOf(pattern);
            if (ki < 0) return 0;
            int colon = src.indexOf(':', ki + pattern.length());
            if (colon < 0) return 0;
            StringBuilder sb = new StringBuilder();
            for (int i = colon + 1; i < src.length(); i++) {
                char c = src.charAt(i);
                if (Character.isDigit(c)) sb.append(c);
                else if (!Character.isWhitespace(c) && sb.length() > 0) break;
            }
            try { return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0; }
            catch (NumberFormatException e) { return 0; }
        }

        List<String> stringArray(String key) {
            String arr = arrayContent(key);
            List<String> result = new ArrayList<>();
            if (arr == null || arr.isBlank()) return result;
            for (String tok : arr.split(",")) {
                String s = tok.trim();
                if (s.startsWith("\"") && s.endsWith("\""))
                    result.add(s.substring(1, s.length() - 1));
            }
            return result;
        }

        String arrayContent(String key) {
            String pattern = "\"" + key + "\"";
            int ki = src.indexOf(pattern);
            if (ki < 0) return null;
            int bracket = src.indexOf('[', ki + pattern.length());
            if (bracket < 0) return null;
            int depth = 0, i = bracket;
            for (; i < src.length(); i++) {
                if (src.charAt(i) == '[') depth++;
                else if (src.charAt(i) == ']') { if (--depth == 0) break; }
            }
            return src.substring(bracket + 1, i);
        }

        static List<String> splitObjects(String arr) {
            List<String> result = new ArrayList<>();
            int depth = 0, start = -1;
            for (int i = 0; i < arr.length(); i++) {
                char c = arr.charAt(i);
                if (c == '{') { if (depth++ == 0) start = i; }
                else if (c == '}') { if (--depth == 0 && start >= 0) result.add(arr.substring(start, i + 1)); }
            }
            return result;
        }
    }
}
