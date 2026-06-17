package com.gmw.gcts.analyzer;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.gmw.gcts.analyzer.preferences.PreferenceConstants;

/** OSGi bundle activator — holds plugin singleton and seeds preference defaults. */
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.gmw.gcts.analyzer";

    private static Activator instance;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        seedDefaults(getPreferenceStore());
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return instance;
    }

    private static void seedDefaults(IPreferenceStore store) {
        store.setDefault(PreferenceConstants.PREF_SYSTEM_URL, "");
        store.setDefault(PreferenceConstants.PREF_USERNAME,   "");
        store.setDefault(PreferenceConstants.PREF_TIMEOUT_S,
                         PreferenceConstants.DEFAULT_TIMEOUT);
    }
}
