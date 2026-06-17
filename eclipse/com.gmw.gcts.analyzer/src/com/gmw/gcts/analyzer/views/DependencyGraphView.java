package com.gmw.gcts.analyzer.views;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.RectangleFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.zest.core.widgets.Graph;
import org.eclipse.zest.core.widgets.GraphConnection;
import org.eclipse.zest.core.widgets.GraphNode;
import org.eclipse.zest.core.widgets.ZestStyles;
import org.eclipse.zest.layouts.LayoutStyles;
import org.eclipse.zest.layouts.algorithms.TreeLayoutAlgorithm;

import com.gmw.gcts.analyzer.model.AnalysisResult;
import com.gmw.gcts.analyzer.model.AnalysisResult.Cluster;
import com.gmw.gcts.analyzer.model.AnalysisResult.Edge;

/**
 * Eclipse View — "gCTS Dependency Graph" (Zest-based visual graph).
 *
 * Renders the dependency analysis as a directed graph:
 *
 *   Nodes  = tasks (coloured by cluster risk)
 *   Edges  = dependency arrows (labelled with kind: IMPLEMENTS, INHERITS, etc.)
 *
 * Risk colour coding:
 *   CRITICAL  →  Red       (same-object conflict)
 *   HIGH      →  Orange    (activation dependency: IMPLEMENTS/INHERITS)
 *   MEDIUM    →  Yellow    (type reference: TYPE_REF/USES/EXTENDS)
 *   NONE      →  Green     (independent task)
 *
 * Requires: org.eclipse.zest.core + org.eclipse.zest.layouts in MANIFEST.MF
 *
 * Usage: shown alongside DependencyResultView. AnalyzeTRHandler opens both.
 */
public class DependencyGraphView extends ViewPart {

    public static final String ID = "com.gmw.gcts.analyzer.views.dependencyGraph";

    private Graph    graph;
    private org.eclipse.swt.widgets.Label headerLabel;

    // ── ViewPart lifecycle ────────────────────────────────────────────────────

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        headerLabel = new org.eclipse.swt.widgets.Label(parent, SWT.NONE);
        headerLabel.setText("gCTS Dependency Graph — no result yet");
        headerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        graph = new Graph(parent, SWT.NONE);
        graph.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        buildToolBar();
    }

    @Override
    public void setFocus() {
        graph.setFocus();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Renders the analysis result as a Zest graph.
     * Safe to call from any thread.
     */
    public void showResult(AnalysisResult result) {
        Display.getDefault().asyncExec(() -> {
            if (graph.isDisposed()) return;

            // Clear previous graph — Zest has no clear(), dispose all child widgets
            for (org.eclipse.zest.core.widgets.GraphItem item : graph.getNodes().toArray(new org.eclipse.zest.core.widgets.GraphItem[0])) {
                item.dispose();
            }
            for (org.eclipse.zest.core.widgets.GraphItem item : graph.getConnections().toArray(new org.eclipse.zest.core.widgets.GraphItem[0])) {
                item.dispose();
            }

            if (result.hasError()) {
                headerLabel.setText("Error: " + result.errorMessage);
                return;
            }

            headerLabel.setText(String.format(
                "TR: %s  —  %d tasks  %d edges  (Zest graph)",
                result.tr, result.taskCount, result.edgeCount));

            buildGraph(result);

            // Apply tree layout: dependencies flow top-down
            graph.setLayoutAlgorithm(
                new TreeLayoutAlgorithm(LayoutStyles.NO_LAYOUT_NODE_RESIZING), true);
        });
    }

    // ── Graph construction ────────────────────────────────────────────────────

    private void buildGraph(AnalysisResult result) {
        Map<String, GraphNode> nodeMap = new HashMap<>();

        // Create one node per task, coloured by cluster risk
        for (Cluster cluster : result.clusters) {
            Color nodeColor = riskColor(cluster.risk);
            for (String task : cluster.tasks) {
                if (!nodeMap.containsKey(task)) {
                    GraphNode node = new GraphNode(graph, SWT.NONE, task);
                    node.setBackgroundColor(nodeColor);
                    node.setTooltip(buildTaskTooltip(task, cluster));
                    nodeMap.put(task, node);
                }
            }
        }

        // Create directed edges for each dependency
        for (Cluster cluster : result.clusters) {
            for (Edge edge : cluster.edges) {
                GraphNode src = nodeMap.get(edge.fromTask);
                GraphNode tgt = nodeMap.get(edge.toTask);
                if (src == null || tgt == null) continue;

                GraphConnection conn = new GraphConnection(
                    graph, ZestStyles.CONNECTIONS_DIRECTED, src, tgt);
                conn.setText(edge.kind);
                conn.setLineColor(edgeColor(edge.kind));
                conn.setTooltip(buildEdgeTooltip(edge));
            }
        }
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    private Color riskColor(String risk) {
        Display d = Display.getDefault();
        return switch (risk) {
            case AnalysisResult.RISK_CRITICAL -> d.getSystemColor(SWT.COLOR_RED);
            case AnalysisResult.RISK_HIGH     -> new Color(d, 255, 140, 0);   // orange
            case AnalysisResult.RISK_MEDIUM   -> new Color(d, 255, 215, 0);   // gold
            default                           -> d.getSystemColor(SWT.COLOR_GREEN);
        };
    }

    private Color edgeColor(String kind) {
        Display d = Display.getDefault();
        return switch (kind) {
            case "IMPLEMENTS", "INHERITS" -> d.getSystemColor(SWT.COLOR_RED);
            case "CONFLICT"               -> new Color(d, 180, 0, 0);         // dark red
            case "TYPE_REF", "USES"       -> d.getSystemColor(SWT.COLOR_DARK_YELLOW);
            default                       -> d.getSystemColor(SWT.COLOR_DARK_GRAY);
        };
    }

    // ── Tooltip builders ──────────────────────────────────────────────────────

    private IFigure buildTaskTooltip(String task, Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(task).append("\n");
        sb.append("Risk: ").append(cluster.risk).append("\n");

        long edgeCount = cluster.edges.stream()
            .filter(e -> e.fromTask.equals(task) || e.toTask.equals(task))
            .count();
        sb.append("Edges: ").append(edgeCount);

        Label lbl = new Label(sb.toString());
        return lbl;
    }

    private IFigure buildEdgeTooltip(Edge edge) {
        String text = edge.kind + ": " + edge.detail + "\n"
                    + edge.fromTask + " → " + edge.toTask;
        return new Label(text);
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void buildToolBar() {
        IToolBarManager tb = getViewSite().getActionBars().getToolBarManager();

        Action expandAll = new Action("Fit Graph") {
            @Override public void run() {
                if (!graph.isDisposed()) graph.applyLayout();
            }
        };
        expandAll.setToolTipText("Re-apply layout and fit graph to view");
        tb.add(expandAll);

        Action toggleDir = new Action("Toggle Direction") {
            private boolean horizontal = false;
            @Override public void run() {
                horizontal = !horizontal;
                // TreeLayoutAlgorithm direction constants: 1=top-down, 2=left-right
                int style = horizontal ? 2 : 1;
                if (!graph.isDisposed())
                    graph.setLayoutAlgorithm(
                        new TreeLayoutAlgorithm(style | LayoutStyles.NO_LAYOUT_NODE_RESIZING),
                        true);
            }
        };
        toggleDir.setToolTipText("Toggle top-down / left-right layout");
        tb.add(toggleDir);
    }
}
