package com.gmw.gcts.analyzer.actions;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.gmw.gcts.analyzer.model.AnalysisResult;
import com.gmw.gcts.analyzer.model.AnalysisResult.Cluster;
import com.gmw.gcts.analyzer.model.AnalysisResult.Edge;
import com.gmw.gcts.analyzer.model.AnalysisResult.PullStep;

/**
 * Toolbar action: exports the current analysis result as CSV.
 *
 * Columns:
 *   TR, RUN_TIMESTAMP, SRC_TASK, SRC_OBJECT, TGT_TASK, TGT_OBJECT,
 *   KIND, RISK, DETAIL, PULL_STEP, PULL_ACTION
 *
 * Added to DependencyResultView toolbar — visible after an analysis completes.
 */
public final class ExportCsvAction extends Action {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Shell          shell;
    private       AnalysisResult result;

    public ExportCsvAction(Shell shell) {
        super("Export CSV");
        this.shell = shell;
        setToolTipText("Export analysis result as CSV (Excel-compatible)");
        setEnabled(false);   // enabled once a result is loaded
    }

    /** Called by DependencyResultView after each successful analysis. */
    public void setResult(AnalysisResult result) {
        this.result = result;
        setEnabled(result != null && !result.hasError());
    }

    @Override
    public void run() {
        if (result == null || result.hasError()) return;

        // Open Save dialog pre-filled with a timestamped filename
        FileDialog dlg = new FileDialog(shell, SWT.SAVE);
        dlg.setFilterExtensions(new String[]{"*.csv", "*.*"});
        dlg.setFilterNames(new String[]{"CSV files (*.csv)", "All files (*.*)"});
        dlg.setFileName(result.tr + "_analysis_" + LocalDateTime.now().format(TS_FORMAT) + ".csv");
        dlg.setOverwrite(true);

        String path = dlg.open();
        if (path == null) return;   // user cancelled

        try {
            String csv = buildCsv(result);
            try (FileWriter fw = new FileWriter(path)) {
                fw.write(csv);
            }
            MessageDialog.openInformation(shell, "gCTS Analyser — Export",
                "CSV saved to:\n" + path + "\n\n" +
                result.edgeCount + " dependency edges exported.");

        } catch (IOException e) {
            MessageDialog.openError(shell, "gCTS Analyser — Export Failed",
                "Could not write CSV:\n" + e.getMessage());
        }
    }

    // ── CSV builder ───────────────────────────────────────────────────────────

    private static String buildCsv(AnalysisResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("TR,RUN_TIMESTAMP,SRC_TASK,SRC_OBJECT,TGT_TASK,TGT_OBJECT," +
                  "KIND,RISK,DETAIL,PULL_STEP,PULL_ACTION\n");

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        for (int ci = 0; ci < r.clusters.size(); ci++) {
            Cluster cluster = r.clusters.get(ci);

            // Determine pull step and action for this cluster
            int    step   = ci + 1;
            String action = pullAction(cluster.risk);

            if (cluster.edges.isEmpty()) {
                // Independent task — one row with no edge detail
                for (String task : cluster.tasks) {
                    sb.append(row(r.tr, ts, task, "", "", "", "NONE", cluster.risk, "", step, action));
                }
            } else {
                for (Edge edge : cluster.edges) {
                    sb.append(row(r.tr, ts,
                        edge.fromTask, edge.from,
                        edge.toTask,   edge.to,
                        edge.kind,     cluster.risk,
                        edge.detail,   step, action));
                }
            }
        }

        // Append pull order summary as a comment block at the end
        sb.append("\n# Pull Order Summary\n");
        sb.append("# STEP,ACTION,TASKS\n");
        for (PullStep ps : r.pullOrder) {
            sb.append("# ").append(ps.step).append(",")
              .append(esc(ps.action)).append(",")
              .append(esc(String.join(" + ", ps.tasks))).append("\n");
        }

        return sb.toString();
    }

    private static String row(String tr, String ts,
                               String srcTask, String srcObj,
                               String tgtTask, String tgtObj,
                               String kind,    String risk,
                               String detail,  int step, String action) {
        return esc(tr)      + "," +
               esc(ts)      + "," +
               esc(srcTask) + "," +
               esc(srcObj)  + "," +
               esc(tgtTask) + "," +
               esc(tgtObj)  + "," +
               esc(kind)    + "," +
               esc(risk)    + "," +
               esc(detail)  + "," +
               step         + "," +
               esc(action)  + "\n";
    }

    /** RFC 4180 CSV escaping — wrap in quotes, double any embedded quotes. */
    private static String esc(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    private static String pullAction(String risk) {
        return switch (risk) {
            case AnalysisResult.RISK_CRITICAL -> "COORDINATE";
            case AnalysisResult.RISK_HIGH     -> "TOGETHER";
            case AnalysisResult.RISK_MEDIUM   -> "TOGETHER_RECOMMENDED";
            default                           -> "ALONE";
        };
    }
}
