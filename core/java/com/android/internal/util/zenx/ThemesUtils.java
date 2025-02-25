/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2020 ZenX-OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.zenx;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.os.RemoteException;
import android.util.Log;

public class ThemesUtils {

    public static final String TAG = "ThemesUtils";

    // Switch themes
    private static final String[] SWITCH_THEMES = {
        "com.android.system.switch.stock", // 0
	    "com.android.system.switch.narrow", // 1
        "com.android.system.switch.contained", // 2
        "com.android.system.switch.telegram", // 3
        "com.android.system.switch.md2", // 4
        "com.android.system.switch.retro", // 5
    };

        public static final String[] QS_TILE_THEMES = {
            "com.android.systemui.qstile.default",
            "com.android.systemui.qstile.square",
            "com.android.systemui.qstile.squircletrim",
            "com.android.systemui.qstile.diamond",
            "com.android.systemui.qstile.star",
            "com.android.systemui.qstile.gear",
            "com.android.systemui.qstile.badge",
            "com.android.systemui.qstile.badgetwo",
            "com.android.systemui.qstile.circletrim",
            "com.android.systemui.qstile.dualtonecircletrim",
            "com.android.systemui.qstile.cookie",
            "com.android.systemui.qstile.circleoutline",
            "com.android.systemui.qstile.wavey",
            "com.android.systemui.qstile.ninja",
            "com.android.systemui.qstile.dottedcircle",
            "com.android.systemui.qstile.attemptmountain",
            "com.android.systemui.qstile.inktober",
            "com.android.systemui.qstile.neonlike",
            "com.android.systemui.qstile.triangles",
            "com.android.systemui.qstile.deletround",
            "com.android.systemui.qstile.bottom_triangle",
    };

    public static final String[] UI_THEMES = {
            "com.android.systemui.ui.default",
            "com.android.systemui.ui.nocornerradius",
            "com.android.systemui.ui.rectangle",
            "com.android.systemui.ui.roundlarge",
            "com.android.systemui.ui.roundmedium",
    };


    public static void updateSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        if (switchStyle == 0) {
            stockSwitchStyle(om, userId);
        } else {
            try {
                om.setEnabled(SWITCH_THEMES[switchStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockSwitchStyle(IOverlayManager om, int userId) {
        for (int i = 0; i < SWITCH_THEMES.length; i++) {
            String switchtheme = SWITCH_THEMES[i];
            try {
                om.setEnabled(switchtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateUIStyle(IOverlayManager om, int userId, int uiStyle) {
        if (uiStyle == 0) {
            stockUIStyle(om, userId);
        } else {
            try {
                om.setEnabled(UI_THEMES[uiStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockUIStyle(IOverlayManager om, int userId) {
        for (int i = 0; i < UI_THEMES.length; i++) {
            String uitheme = UI_THEMES[i];
            try {
                om.setEnabled(uitheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

}
