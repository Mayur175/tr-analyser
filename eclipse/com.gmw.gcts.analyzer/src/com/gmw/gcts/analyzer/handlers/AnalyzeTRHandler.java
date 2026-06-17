package com.gmw.gcts.analyzer.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.gmw.gcts.analyzer.client.AnalyzerHttpClient;
import com.gmw.gcts.analyzer.model.AnalysisResult;
import com.gmw.gcts.analyzer.views.DependencyGraphView;
import com.gmw.gcts.analyzer.views.DependencyResultView;

/**
 * Command handler for "Analyse gCTS Dependencies".
 *
 * Flow (Phase 2+3 — no clipboard, no F9):
 *   1. Detect TR via TrDetector (IAdaptable first, regex toString fallback)
 *   2. InputDialog — pre-filled, user confirms or edits
 *   3. Open DependencyResultView + DependencyGraphView → show loading state
 *   4. Background thread: AnalyzerHttpClient.analyze(tr) → ICF REST call
 *   5. Both views receive showResult() — marshalled to UI thread
 */
public class AnalyzeTRHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        ISelection sel = HandlerUtil.getCurrentSelection(event);

        String detected = detectTrFromSelection(sel);
        String tr = promptForTr(shell, detected);
        if (tr == null) return null;

        runAnalysis(tr, shell);
        return null;
    }

    // ── Step 1: TR detection (IAdaptable → regex fallback) ────────────────────

    private String detectTrFromSelection(ISelection selection) {
        if (!(selection instanceof IStructuredSelection ss)) return null;
        return TrDetector.detect(ss);
    }

    // ── Step 2: Confirm TR via InputDialog ────────────────────────────────────

    private String promptForTr(Shell shell, String detected) {
        InputDialog dlg = new InputDialog(
            shell,
            "gCTS Dependency Analyser",
            "Transport Request number (e.g. GMWK900691):",
            detected != null ? detected : "",
            input -> {
                if (input == null || input.isBlank())
                    return "Please enter a TR number.";
                if (!TrDetector.isValidTr(input.trim()))
                    return "Invalid format. Expected: [A-Z0-9]{3,4}K[0-9]{6}  e.g. GMWK900691";
                return null;
            });
        return dlg.open() == Window.OK ? dlg.getValue().trim().toUpperCase() : null;
    }

    // ── Steps 3–5: Open views, call ICF, render result ────────────────────────

    private void runAnalysis(String tr, Shell shell) {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                                        .getActiveWorkbenchWindow()
                                        .getActivePage();

        DependencyResultView tableView = openTableView(page, shell);
        DependencyGraphView  graphView = openGraphView(page);  // optional — no error if unavailable

        if (tableView == null) return;

        tableView.showLoading(tr);
        if (graphView != null) graphView.showResult(emptyLoadingResult(tr));

        new Thread(() -> {
            AnalyzerHttpClient client = new AnalyzerHttpClient();
            AnalysisResult result = client.analyze(tr);
            tableView.showResult(result);
            if (graphView != null) graphView.showResult(result);
        }, "gcts-analyzer-" + tr).start();
    }

    private DependencyResultView openTableView(IWorkbenchPage page, Shell shell) {
        try {
            IViewPart part = page.showView(DependencyResultView.ID, null,
                                           IWorkbenchPage.VIEW_ACTIVATE);
            return part instanceof DependencyResultView v ? v : null;
        } catch (PartInitException e) {
            MessageDialog.openError(shell, "gCTS Analyser",
                "Could not open Dependency Analysis view:\n" + e.getMessage());
            return null;
        }
    }

    private DependencyGraphView openGraphView(IWorkbenchPage page) {
        try {
            // VIEW_CREATE (not ACTIVATE) so the table view stays in front
            IViewPart part = page.showView(DependencyGraphView.ID, null,
                                           IWorkbenchPage.VIEW_CREATE);
            return part instanceof DependencyGraphView v ? v : null;
        } catch (Exception e) {
            return null;  // Zest not installed — graph view silently absent
        }
    }

    /** Placeholder result shown in graph view while HTTP call is in flight. */
    private static AnalysisResult emptyLoadingResult(String tr) {
        return AnalysisResult.error("Loading TR " + tr + " …");
    }
}
