# SICF Service Activation Guide — ZGCTS Analyzer

## Overview

The ICF (Internet Communication Framework) service exposes the ABAP analysis
pipeline as a REST endpoint. The Eclipse plugin calls this instead of using
clipboard + F9.

**Endpoint:** `GET /sap/bc/zgcts/analyze?tr=GMWK900691`
**Handler:**  `ZGCTS_ANALYZE_HANDLER`

---

## Step 1 — Create Class ZGCTS_ANALYZE_HANDLER in ADT

1. Open Eclipse ADT → connect to your BTP ABAP / S/4HANA Cloud system
2. Right-click your Z-package → **New → ABAP Class**
3. Name: `ZGCTS_ANALYZE_HANDLER`
4. Description: `ICF handler for gCTS dependency analyzer`
5. Interfaces: add `IF_HTTP_EXTENSION`
6. Click **Finish**
7. Paste content from `zgcts_analyze_handler/zgcts_analyze_handler.clas.abap`
8. Activate: **Ctrl+F3**

---

## Step 2 — Register the ICF Service (Transaction SICF)

> SICF is available in both BTP ABAP (restricted) and on-premise systems.
> In BTP ABAP use the Fiori Launchpad → *Maintain ICF Services* app instead
> if direct SICF access is unavailable.

1. Run transaction **SICF**
2. Hierarchy type: **SERVICE** → Execute (F8)
3. Navigate to: `default_host → sap → bc`
4. Right-click node `bc` → **New Sub-Element**
5. Service name: `zgcts`
6. Description: `gCTS Analyzer root`
7. Click **OK** — node `zgcts` created under `bc`
8. Right-click node `zgcts` → **New Sub-Element**
9. Service name: `analyze`
10. Description: `gCTS TR dependency analysis`
11. On the **Handler List** tab → Add row → Class: `ZGCTS_ANALYZE_HANDLER`
12. Click **Save**

---

## Step 3 — Activate the Service

1. Right-click the `analyze` node → **Activate Service**
2. Confirm the dialog
3. The node icon turns green — service is live

---

## Step 4 — Assign Logon Procedure

1. Click the `analyze` node → **Properties** tab
2. Under **Logon Data** → select `Basic Authentication` or `SAML2`
   - For ADT plugin: use the same credentials as the ADT system connection
3. Save

---

## Step 5 — Test the Endpoint

**From browser (Basic Auth):**
```
https://<your-system-host>/sap/bc/zgcts/analyze?tr=GMWK900691
```

**From curl:**
```bash
curl -u "developer:password" \
     "https://<host>/sap/bc/zgcts/analyze?tr=GMWK900691"
```

**Expected response:**
```json
{
  "tr": "GMWK900691",
  "taskCount": 4,
  "objectCount": 12,
  "edgeCount": 3,
  "clusters": [
    {
      "risk": "HIGH",
      "tasks": ["GMWK900692", "GMWK900693"],
      "edges": [
        { "from": "CLAS/ZCL_FOO", "fromTask": "GMWK900692",
          "to": "INTF/ZIF_FOO",   "toTask":   "GMWK900693",
          "kind": "IMPLEMENTS",   "detail": "ZCL_FOO implements ZIF_FOO" }
      ]
    }
  ],
  "pullOrder": [
    { "step": 1, "action": "TOGETHER",  "tasks": ["GMWK900692","GMWK900693"] },
    { "step": 2, "action": "ALONE",     "tasks": ["GMWK900694"] }
  ]
}
```

**Error responses:**
```json
{ "error": "Missing query parameter: tr" }         // HTTP 400
{ "error": "Invalid TR format 'XYZ'" }             // HTTP 400
{ "error": "<ABAP exception text>" }               // HTTP 500
```

---

## Step 6 — Configure the Eclipse Plugin

After the service is running, open Eclipse:

1. **Window → Preferences → gCTS Tools → Dependency Analyzer**
2. **System URL:** `https://<your-system-host>`
   - Example: `https://my-abap.hana.ondemand.com`
3. **Username / Password:** your SAP user credentials
   - Leave blank to use the active ADT project connection credentials
4. Click **Test Connection** — should show "Connection OK"
5. **Apply and Close**

---

## Authorisation Objects Required

The user calling the ICF service needs:

| Object  | Field   | Value                     |
|---------|---------|---------------------------|
| S_ICF   | ICF_VALUE | `/sap/bc/zgcts/analyze` |
| S_CTS_ADMI | CTS_ADMFCT | DISP            |

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| HTTP 404 | Service node not found | Check SICF path: `/sap/bc/zgcts/analyze` |
| HTTP 403 | Not activated or missing auth | Activate service in SICF; check S_ICF auth |
| HTTP 500 | ABAP exception | Check SM21 / ST22 for short dump details |
| Empty clusters `[]` | TR has no tasks or wrong TR number | Verify TR in SE09 / Transport Organizer |
| `XCO_CP_CTS` not found | On-premise ECC system | XCO APIs are BTP/Cloud only |
