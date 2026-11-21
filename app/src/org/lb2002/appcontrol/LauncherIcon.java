// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 Yicons

package org.lb2002.appcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

final class LauncherIcon {
    static void refresh(Context context) {
        boolean frozen = false;
        for (ApplicationInfo info : context.getPackageManager().getInstalledApplications(0)) {
            if (!info.enabled) { frozen = true; break; }
        }
        update(context, frozen);
    }

    static void update(Context context, boolean frozen) {
        PackageManager pm = context.getPackageManager();
        ComponentName active = new ComponentName(context,
            frozen ? "org.lb2002.appcontrol.FrozenLauncher" : "org.lb2002.appcontrol.NormalLauncher");
        ComponentName inactive = new ComponentName(context,
            frozen ? "org.lb2002.appcontrol.NormalLauncher" : "org.lb2002.appcontrol.FrozenLauncher");
        try {
            if (pm.getComponentEnabledSetting(active) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(active, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            }
            if (pm.getComponentEnabledSetting(inactive) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(inactive, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException e) {
            Log.w("LB2002AppControl", "Cannot update launcher icon", e);
        }
    }
}
