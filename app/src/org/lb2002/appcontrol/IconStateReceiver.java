// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 Yicons

package org.lb2002.appcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class IconStateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent.getData() != null
                && context.getPackageName().equals(intent.getData().getSchemeSpecificPart())) return;
        PendingResult result = goAsync();
        new Thread(() -> {
            try { LauncherIcon.refresh(context); }
            catch (RuntimeException e) { Log.w("LB2002AppControl", "Icon refresh failed", e); }
            finally { result.finish(); }
        }, "UpdateLauncherIcon").start();
    }
}
