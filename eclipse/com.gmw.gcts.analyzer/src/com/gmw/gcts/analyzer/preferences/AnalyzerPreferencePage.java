package com.gmw.gcts.analyzer.preferences;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.gmw.gcts.analyzer.Activator;
import com.gmw.gcts.analyzer.client.AnalyzerHttpClient;

/**
 * Preference page: Window → Preferences → gCTS Tools → Dependency Analyzer.
 *
 * Stores:
 *   - System URL     → standard preference store
 *   - Username       → standard preference store
 *   - Password       → Eclipse Secure Storage (encrypted)
 *   - Timeout        → standard preference store
 */
public class AnalyzerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private StringFieldEditor urlEditor;
    private StringFieldEditor userEditor;
    private Text              passwordText;
    private Text              timeoutText;

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Connection settings for the SAP ICF endpoint /sap/bc/zgcts/analyze");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(2, false));

        // ── System URL ────────────────────────────────────────────────────────
        urlEditor = new StringFieldEditor(
            PreferenceConstants.PREF_SYSTEM_URL,
            "SAP System URL:", container);
        urlEditor.setPage(this);
        urlEditor.setPreferenceStore(getPreferenceStore());
        urlEditor.load();
        setFieldHint(urlEditor, "https://my-system.hana.ondemand.com");

        // ── Username ──────────────────────────────────────────────────────────
        userEditor = new StringFieldEditor(
            PreferenceConstants.PREF_USERNAME,
            "Username:", container);
        userEditor.setPage(this);
        userEditor.setPreferenceStore(getPreferenceStore());
        userEditor.load();

        // ── Password (Secure Storage) ─────────────────────────────────────────
        new Label(container, SWT.NONE).setText("Password:");
        passwordText = new Text(container, SWT.BORDER | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        passwordText.setText(loadSecurePassword());

        // ── Timeout ───────────────────────────────────────────────────────────
        new Label(container, SWT.NONE).setText("Timeout (seconds):");
        timeoutText = new Text(container, SWT.BORDER);
        timeoutText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        int savedTimeout = getPreferenceStore().getInt(PreferenceConstants.PREF_TIMEOUT_S);
        timeoutText.setText(String.valueOf(savedTimeout == 0
            ? PreferenceConstants.DEFAULT_TIMEOUT : savedTimeout));

        // ── Separator ─────────────────────────────────────────────────────────
        Label sep = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        // ── Test Connection button ────────────────────────────────────────────
        Button testBtn = new Button(container, SWT.PUSH);
        testBtn.setText("Test Connection");
        testBtn.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        testBtn.addListener(SWT.Selection, e -> testConnection(parent));

        return container;
    }

    @Override
    protected void performDefaults() {
        urlEditor.loadDefault();
        userEditor.loadDefault();
        passwordText.setText("");
        timeoutText.setText(String.valueOf(PreferenceConstants.DEFAULT_TIMEOUT));
        super.performDefaults();
    }

    @Override
    public boolean performOk() {
        urlEditor.store();
        userEditor.store();
        saveSecurePassword(passwordText.getText());
        try {
            int t = Integer.parseInt(timeoutText.getText().trim());
            getPreferenceStore().setValue(PreferenceConstants.PREF_TIMEOUT_S,
                t > 0 ? t : PreferenceConstants.DEFAULT_TIMEOUT);
        } catch (NumberFormatException e) {
            getPreferenceStore().setValue(PreferenceConstants.PREF_TIMEOUT_S,
                PreferenceConstants.DEFAULT_TIMEOUT);
        }
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void testConnection(Composite parent) {
        // Temporarily persist values so AnalyzerHttpClient reads them
        performOk();

        AnalyzerHttpClient client = new AnalyzerHttpClient();
        String error = client.testConnection();

        MessageBox mb = new MessageBox(parent.getShell(),
            error == null ? SWT.ICON_INFORMATION | SWT.OK
                          : SWT.ICON_ERROR       | SWT.OK);
        mb.setText("gCTS Analyzer — Connection Test");
        mb.setMessage(error == null
            ? "Connection OK — SAP ICF service is reachable."
            : "Connection failed:\n\n" + error);
        mb.open();
    }

    private static void setFieldHint(StringFieldEditor editor, String hint) {
        // Best-effort: set message text on the underlying Text control
        try {
            Text t = (Text) editor.getTextControl(
                (Composite) editor.getLabelControl(null).getParent());
            t.setMessage(hint);
        } catch (Exception ignored) {}
    }

    private static String loadSecurePassword() {
        try {
            ISecurePreferences node = SecurePreferencesFactory.getDefault()
                                          .node("com.gmw.gcts.analyzer");
            return node.get(PreferenceConstants.PREF_PASSWORD, "");
        } catch (Exception e) {
            return "";
        }
    }

    private static void saveSecurePassword(String password) {
        try {
            ISecurePreferences node = SecurePreferencesFactory.getDefault()
                                          .node("com.gmw.gcts.analyzer");
            node.put(PreferenceConstants.PREF_PASSWORD, password, /*encrypt=*/true);
        } catch (Exception ignored) {}
    }
}
