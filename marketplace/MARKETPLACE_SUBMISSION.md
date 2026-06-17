# Eclipse Marketplace Submission Guide

## Overview

This document covers the steps to publish the gCTS Task Dependency Analyzer
to the Eclipse Marketplace so any Eclipse/ADT user can install it via
`Help → Eclipse Marketplace`.

---

## Pre-Submission Checklist

Before submitting, ensure all of the following are true:

| Item | Status |
|------|--------|
| Plugin JAR built via `mvn clean package` | ⬜ |
| P2 update site deployed to GitHub Pages (or another public URL) | ⬜ |
| Update site URL is publicly reachable without auth | ⬜ |
| `feature.xml` has correct version, description, and license | ⬜ |
| `marketplace.xml` `updateURL` updated to real GitHub Pages URL | ⬜ |
| Screenshots captured and hosted (at least 1 required) | ⬜ |
| Plugin tested end-to-end: right-click TR → View renders → CSV exports | ⬜ |

---

## Step 1 — Build and Deploy the P2 Update Site

```bash
cd "TR dependency/eclipse"
mvn clean package

# Output:
# com.gmw.gcts.analyzer.updatesite/target/repository/
#   content.xml
#   artifacts.xml
#   features/com.gmw.gcts.analyzer.feature_1.0.0.jar
#   plugins/com.gmw.gcts.analyzer_1.0.0.jar
```

Deploy the `repository/` folder to GitHub Pages:

```bash
# Option A — GitHub Pages via gh-pages branch
git checkout --orphan gh-pages
cp -r eclipse/com.gmw.gcts.analyzer.updatesite/target/repository/* ./updatesite/
git add updatesite/
git commit -m "Publish P2 update site v1.0.0"
git push origin gh-pages

# Resulting install URL:
# https://<org>.github.io/<repo>/updatesite

# Option B — GitHub Actions (automated, see .github/workflows/release.yml)
git tag v1.0.0
git push origin v1.0.0
# CI builds and deploys automatically
```

---

## Step 2 — Create an Eclipse Foundation Account

1. Go to: https://accounts.eclipse.org/user/register
2. Register with your organisation email
3. Sign the Eclipse Contributor Agreement (ECA) — required for all listings

---

## Step 3 — Create the Marketplace Listing

1. Go to: https://marketplace.eclipse.org/
2. Click **Submit Your Solution**
3. Fill in the form — use the values from `marketplace/marketplace.xml`:

| Field | Value |
|-------|-------|
| **Name** | gCTS Task Dependency Analyzer |
| **Short description** | Detects cross-task object dependencies in SAP gCTS TRs |
| **Categories** | Team Development, Tools |
| **Minimum Eclipse version** | 2023-03 |
| **Install URL (Update Site)** | `https://<org>.github.io/<repo>/updatesite` |
| **License** | Apache 2.0 |
| **Tags** | SAP, ABAP, gCTS, ADT, Transport, S4HANA, BTP |

4. In the **Description** tab, paste the HTML body from `marketplace.xml` `<body>` section
5. Upload at least one screenshot (1280×800 recommended)

---

## Step 4 — Import from XML (Alternative)

If the Marketplace supports XML import:

1. Go to your listing draft → **Import** tab
2. Upload `marketplace/marketplace.xml`
3. Review pre-filled fields → adjust as needed

---

## Step 5 — Submit for Review

1. Click **Submit for Review**
2. Eclipse Marketplace team reviews within 3–5 business days
3. Listing goes live once approved

---

## Step 6 — Verify Install from Marketplace

Once live:

1. In Eclipse: **Help → Eclipse Marketplace**
2. Search: `gCTS`
3. Click **Install** next to *gCTS Task Dependency Analyzer*
4. Accept licence → Finish → Restart
5. Verify: **gCTS Tools** menu appears in menu bar

---

## Updating the Listing

For each new release:

1. Tag the release: `git tag v1.x.x && git push origin v1.x.x`
2. GitHub Actions builds and publishes the new P2 site automatically
3. The Marketplace listing points to the same URL — users get the new version
   next time they check for updates via **Help → Check for Updates**

---

## Screenshots to Capture

| Filename | What to show |
|----------|-------------|
| `result-view.png` | DependencyResultView with a real TR result — clusters, edges, pull order |
| `graph-view.png` | DependencyGraphView with coloured task nodes and directed edges |
| `context-menu.png` | Right-click on a TR in Transport Organizer showing "Analyse Dependencies…" |
| `preferences.png` | Preferences page — URL, username, Test Connection button |
| `csv-export.png` | File save dialog after clicking Export CSV |

Host under: `docs/screenshots/` and reference from `marketplace.xml`.
