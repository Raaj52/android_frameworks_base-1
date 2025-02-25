/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.annotation.ColorInt;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.StatsLog;
import android.view.ContextThemeWrapper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;

import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.util.zenx.ZenxUtils;
import com.android.settingslib.Utils;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.DualToneHandler;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.privacy.OngoingPrivacyChip;
import com.android.systemui.privacy.PrivacyDialogBuilder;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyItemControllerKt;
import com.android.systemui.qs.QSDetail.Callback;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.settings.BrightnessController;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View that contains the top-most bits of the screen (primarily the status bar with date, time, and
 * battery) and also contains the {@link QuickQSPanel} along with some of the panel's inner
 * contents.
 */
public class QuickStatusBarHeader extends RelativeLayout implements
        View.OnClickListener, NextAlarmController.NextAlarmChangeCallback,
        ZenModeController.Callback {
    private static final String TAG = "QuickStatusBarHeader";
    private static final boolean DEBUG = false;
    public static final String QS_SHOW_INFO_HEADER = "qs_show_info_header";

    /** Delay for auto fading out the long press tooltip after it's fully visible (in ms). */
    private static final long AUTO_FADE_OUT_DELAY_MS = DateUtils.SECOND_IN_MILLIS * 6;
    private static final int FADE_ANIMATION_DURATION_MS = 300;
    private static final int TOOLTIP_NOT_YET_SHOWN_COUNT = 0;
    public static final int MAX_TOOLTIP_SHOWN_COUNT = 2;

    private final Handler mHandler = new Handler();
    private final NextAlarmController mAlarmController;
    private final ZenModeController mZenController;
    private final StatusBarIconController mStatusBarIconController;
    private final ActivityStarter mActivityStarter;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mListening;
    private boolean mQsDisabled;
    private boolean isShowDragHandle;
    private boolean isAlwaysShowSettings;

    private QSCarrierGroup mCarrierGroup;
    protected QuickQSPanel mHeaderQsPanel;
    protected QSTileHost mHost;
    private TintedIconManager mIconManager;
    private TouchAnimator mStatusIconsAlphaAnimator;
    private TouchAnimator mHeaderTextContainerAlphaAnimator;
    private TouchAnimator mPrivacyChipAlphaAnimator;
    private DualToneHandler mDualToneHandler;

    private View mSystemIconsView;
    private View mQuickQsStatusIcons;
    private View mHeaderTextContainerView;

    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private ImageView mNextAlarmIcon;
    /** {@link TextView} containing the actual text indicating when the next alarm will go off. */
    private TextView mNextAlarmTextView;
    private View mNextAlarmContainer;
    private View mStatusSeparator;
    private ImageView mRingerModeIcon;
    private TextView mRingerModeTextView;
    private View mRingerContainer;
    private Clock mClockView;
    private DateView mDateView;
    private OngoingPrivacyChip mPrivacyChip;
    private Space mSpace;
    private BatteryMeterView mBatteryRemainingIcon;
    private boolean mPermissionsHubEnabled;
    private View mQuickQsBrightness;
    private BrightnessController mBrightnessController;
    private boolean mIsQuickQsBrightnessEnabled;
    private boolean mIsQsAutoBrightnessEnabled;

    private PrivacyItemController mPrivacyItemController;

    private boolean mLandscape;
    private boolean mHeaderImageEnabled;
    private boolean mForceHideQsStatusBar;
    private BatteryMeterView mBatteryMeterView;
    private int mQSBatteryMode = 0;
    private int mSysBatteryMode = 0;

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    private TextView mSystemInfoText;
    private int mSystemInfoMode;
    private View mSystemInfoLayout;
    private ImageView mSystemInfoIcon;

    private final BroadcastReceiver mRingerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mRingerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
            updateStatusText();
        }
    };
    private boolean mHasTopCutout = false;
    private boolean mPrivacyChipLogged = false;

    private final DeviceConfig.OnPropertyChangedListener mPropertyListener =
            new DeviceConfig.OnPropertyChangedListener() {
                @Override
                public void onPropertyChanged(String namespace, String name, String value) {
                    if (DeviceConfig.NAMESPACE_PRIVACY.equals(namespace)
                            && SystemUiDeviceConfigFlags.PROPERTY_PERMISSIONS_HUB_ENABLED.equals(
                            name)) {
                        mPermissionsHubEnabled = Boolean.valueOf(value);
                        StatusIconContainer iconContainer = findViewById(R.id.statusIcons);
                        iconContainer.setIgnoredSlots(getIgnoredIconSlots());
                    }
                }
            };

    private PrivacyItemController.Callback mPICCallback = new PrivacyItemController.Callback() {
        @Override
        public void privacyChanged(List<PrivacyItem> privacyItems) {
            mPrivacyChip.setPrivacyList(privacyItems);
            setChipVisibility(!privacyItems.isEmpty());
        }
    };

    @Inject
    public QuickStatusBarHeader(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            NextAlarmController nextAlarmController, ZenModeController zenModeController,
            StatusBarIconController statusBarIconController,
            ActivityStarter activityStarter, PrivacyItemController privacyItemController) {
        super(context, attrs);
        mAlarmController = nextAlarmController;
        mZenController = zenModeController;
        mStatusBarIconController = statusBarIconController;
        mActivityStarter = activityStarter;
        mPrivacyItemController = privacyItemController;
        mDualToneHandler = new DualToneHandler(
                new ContextThemeWrapper(context, R.style.QSHeaderTheme));
        mSystemInfoMode = getQsSystemInfoMode();
        mQSBatteryMode = getQSBatteryMode();
        mSysBatteryMode = getSysBatteryMode();
        mSettingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mSystemIconsView = findViewById(R.id.quick_status_bar_system_icons);
        mQuickQsStatusIcons = findViewById(R.id.quick_qs_status_icons);
        StatusIconContainer iconContainer = findViewById(R.id.statusIcons);
        // Ignore privacy icons because they show in the space above QQS
        iconContainer.addIgnoredSlots(getIgnoredIconSlots());
        iconContainer.setShouldRestrictIcons(false);
        mIconManager = new TintedIconManager(iconContainer);

        mQuickQsBrightness = findViewById(R.id.quick_qs_brightness_bar);
        mBrightnessController = new BrightnessController(getContext(),
                mQuickQsBrightness.findViewById(R.id.brightness_icon),
                mQuickQsBrightness.findViewById(R.id.brightness_slider));

        // Views corresponding to the header info section (e.g. ringer and next alarm).
        mHeaderTextContainerView = findViewById(R.id.header_text_container);
        mStatusSeparator = findViewById(R.id.status_separator);
        mNextAlarmIcon = findViewById(R.id.next_alarm_icon);
        mNextAlarmTextView = findViewById(R.id.next_alarm_text);
        mNextAlarmContainer = findViewById(R.id.alarm_container);
        mNextAlarmContainer.setOnClickListener(this::onClick);
        mRingerModeIcon = findViewById(R.id.ringer_mode_icon);
        mRingerModeTextView = findViewById(R.id.ringer_mode_text);
        mRingerContainer = findViewById(R.id.ringer_container);
        mRingerContainer.setOnClickListener(this::onClick);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mPrivacyChip.setOnClickListener(this::onClick);
        mCarrierGroup = findViewById(R.id.carrier_group);
        mForceHideQsStatusBar = mContext.getResources().getBoolean(R.bool.qs_status_bar_hidden);
        mSystemInfoLayout = findViewById(R.id.system_info_layout);
        mSystemInfoIcon = findViewById(R.id.system_info_icon);
        mSystemInfoText = findViewById(R.id.system_info_text);

        updateResources();

        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);

        // Set light text on the header icons because they will always be on a black background
        applyDarkness(R.id.clock, tintArea, 0, DarkIconDispatcher.DEFAULT_ICON_TINT);

        // Set the correct tint for the status icons so they contrast
        mIconManager.setTint(fillColor);
        mNextAlarmIcon.setImageTintList(ColorStateList.valueOf(fillColor));
        mRingerModeIcon.setImageTintList(ColorStateList.valueOf(fillColor));

        mBatteryMeterView = findViewById(R.id.battery);
        mBatteryMeterView.setOnClickListener(this);

        mClockView = findViewById(R.id.clock);
        mClockView.setOnClickListener(this);
	    mClockView.setQsHeader();
        mDateView = findViewById(R.id.date);
        mDateView.setOnClickListener(this);
        mSpace = findViewById(R.id.space);

        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        mBatteryRemainingIcon.setOnClickListener(this);
        mRingerModeTextView.setSelected(true);
        mNextAlarmTextView.setSelected(true);

        mPermissionsHubEnabled = PrivacyItemControllerKt.isPermissionsHubEnabled();;
        // Change the ignored slots when DeviceConfig flag changes
        DeviceConfig.addOnPropertyChangedListener(DeviceConfig.NAMESPACE_PRIVACY,
                mContext.getMainExecutor(), mPropertyListener);

        setQSBatteryVisibility();
        setSysBatteryVisibility();
        updateSettings();
    }

    private int getBatteryPercentMode(boolean showBatteryEstimate) {
        return showBatteryEstimate ?
               BatteryMeterView.MODE_ESTIMATE : BatteryMeterView.MODE_ON;
    }

    public void setQSBatteryVisibility() {
        mQSBatteryMode = getQSBatteryMode();
        switch(mQSBatteryMode) {
            case 0:
                mBatteryRemainingIcon.setVisibility(View.GONE);
                break;
            case 1:
                mBatteryRemainingIcon.setVisibility(View.VISIBLE);
                mBatteryRemainingIcon.setIsQsHeader(true);
                mBatteryRemainingIcon.setPercentShowMode(getBatteryPercentMode(true));
                break;
            case 2:
                mBatteryRemainingIcon.setVisibility(View.VISIBLE);
                mBatteryRemainingIcon.setIsQsHeader(true);
                mBatteryRemainingIcon.setPercentShowMode(getBatteryPercentMode(false));
        }
    }

    private int getQSBatteryMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_BATTERY_MODE, 0);
    }

    private void setSysBatteryVisibility() {
        mSysBatteryMode = getSysBatteryMode();
        switch(mSysBatteryMode) {
            case 0:
                mBatteryMeterView.setVisibility(View.GONE);
                break;
            case 1:
                mBatteryMeterView.setVisibility(View.VISIBLE);
                mBatteryMeterView.setIsQsHeader(false);
                mBatteryMeterView.setPercentShowMode(getBatteryPercentMode(true));
                break;
            case 2:
                mBatteryMeterView.setVisibility(View.VISIBLE);
                mBatteryMeterView.setIsQsHeader(false);
                mBatteryMeterView.setPercentShowMode(getBatteryPercentMode(false));
        }
    }

    private int getSysBatteryMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_SYS_BATTERY_MODE, 0);
    }

    private List<String> getIgnoredIconSlots() {
        ArrayList<String> ignored = new ArrayList<>();
        if (mQSBatteryMode == 1 || mQSBatteryMode == 2) {
            ignored.add(mContext.getResources().getString(
                    com.android.internal.R.string.status_bar_camera));
            ignored.add(mContext.getResources().getString(
                    com.android.internal.R.string.status_bar_microphone));
            ignored.add(mContext.getResources().getString(
                    com.android.internal.R.string.status_bar_location));
        }

        return ignored;
    }

    private void updateStatusText() {
        boolean changed = updateRingerStatus() || updateAlarmStatus();

        if (changed) {
            boolean alarmVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
            boolean ringerVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
            mStatusSeparator.setVisibility(alarmVisible && ringerVisible ? View.VISIBLE
                    : View.GONE);
        }
    }

    public boolean showPrivacyChip() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.SHOW_PRIVACY_CHIP, 0) == 1;
    }

    private void setChipVisibility(boolean chipVisible) {
        if (chipVisible && mPermissionsHubEnabled && showPrivacyChip()) {
            mPrivacyChip.setVisibility(View.VISIBLE);
            mSystemInfoLayout.setVisibility(View.GONE);
            // Makes sure that the chip is logged as viewed at most once each time QS is opened
            // mListening makes sure that the callback didn't return after the user closed QS
            if (!mPrivacyChipLogged && mListening) {
                mPrivacyChipLogged = true;
                StatsLog.write(StatsLog.PRIVACY_INDICATORS_INTERACTED,
                        StatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__CHIP_VIEWED);
            }
        } else {
            mPrivacyChip.setVisibility(View.GONE);
            mSystemInfoLayout.setVisibility(View.VISIBLE);
        }
    }

    private int getQsSystemInfoMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_SYSTEM_INFO, 0);
    }

    public boolean isQsSystemInfoIconEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_SYSTEM_INFO_ICON, 1) == 1;
    }

    private void updateSystemInfoText() {
        if (mSystemInfoMode == 0) {
            mSystemInfoText.setVisibility(View.GONE);
            mSystemInfoIcon.setVisibility(View.GONE);
            return;
        } else {
            mSystemInfoText.setVisibility(View.VISIBLE);
            if(isQsSystemInfoIconEnabled()) {
                mSystemInfoIcon.setVisibility(View.VISIBLE);
            } else {
                mSystemInfoIcon.setVisibility(View.GONE);
            }
        }

        switch (mSystemInfoMode) {
            case 1:
                mSystemInfoIcon.setImageDrawable(getContext().getDrawable(R.drawable.ic_thermometer));
                mSystemInfoText.setText(getCPUTemp());
                break;
            case 2:
                mSystemInfoIcon.setImageDrawable(getContext().getDrawable(R.drawable.ic_thermometer));
                mSystemInfoText.setText(getBatteryTemp());
                break;
            case 3:
                mSystemInfoIcon.setImageDrawable(getContext().getDrawable(R.drawable.ic_gpu));
                mSystemInfoText.setText(getGPUClock());
                break;
            case 4:
                mSystemInfoIcon.setImageDrawable(getContext().getDrawable(R.drawable.ic_gpu));
                mSystemInfoText.setText(getGPUBusy());
                break;
            default:
                mSystemInfoText.setVisibility(View.GONE);
                mSystemInfoIcon.setVisibility(View.GONE);
            break;
            }
    }

    private String getBatteryTemp() {
        String value;
        if(ZenxUtils.fileExists(getContext().getResources().getString(
                     com.android.internal.R.string.config_battery_temp_path))) {
                        value = readOneLine(getContext().getResources().getString(
                            com.android.internal.R.string.config_battery_temp_path));
                     } else {
                         value = "Error";
                     }

        return value == "Error" ? "N/A" : String.format("%s", Integer.parseInt(value) / 10) + "\u2103";
    }

    private String getCPUTemp() {
        String value;
        if(ZenxUtils.fileExists(getContext().getResources().getString(
                     com.android.internal.R.string.config_cpu_temp_path))) {
                        value = readOneLine(getContext().getResources().getString(
                            com.android.internal.R.string.config_cpu_temp_path));
                     } else {
                         value = "Error";
                     }

        return value == "Error" ? "N/A" : String.format("%s", Integer.parseInt(value) / 1000) + "\u2103";
    }

    private String getGPUBusy() {
        String value;
        if(ZenxUtils.fileExists(getContext().getResources().getString(
                     com.android.internal.R.string.config_gpu_busy_path))) {
                        value = readOneLine(getContext().getResources().getString(
                            com.android.internal.R.string.config_gpu_busy_path));
                     } else {
                         value = "Error";
                     }

        return value == "Error" ? "N/A" : value;
    }

    private String getGPUClock() {
        String value;
        if(ZenxUtils.fileExists(getContext().getResources().getString(
                     com.android.internal.R.string.config_gpu_clock_path))) {
                        value = readOneLine(getContext().getResources().getString(
                            com.android.internal.R.string.config_gpu_clock_path));
                     } else {
                         value = "Error";
                     }

        return value == "Error" ? "N/A" : String.format("%s", Integer.parseInt(value)) + "Mhz";
    }
    
    private static String readOneLine(String fname) {
        BufferedReader br;
        String line = null;
        try {
            br = new BufferedReader(new FileReader(fname), 512);
            try {
                line = br.readLine();
            } finally {
                br.close();
            }
        } catch (Exception e) {
            return null;
        }
        return line;
    }

    private boolean updateRingerStatus() {
        boolean isOriginalVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
        CharSequence originalRingerText = mRingerModeTextView.getText();

        boolean ringerVisible = false;
        if (!ZenModeConfig.isZenOverridingRinger(mZenController.getZen(),
                mZenController.getConsolidatedPolicy())) {
            if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_vibrate);
                mRingerModeTextView.setText(R.string.qs_status_phone_vibrate);
                ringerVisible = true;
            } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_mute);
                mRingerModeTextView.setText(R.string.qs_status_phone_muted);
                ringerVisible = true;
            }
        }
        mRingerModeIcon.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);
        mRingerModeTextView.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);
        mRingerContainer.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != ringerVisible ||
                !Objects.equals(originalRingerText, mRingerModeTextView.getText());
    }

    private boolean updateAlarmStatus() {
        boolean isOriginalVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
        CharSequence originalAlarmText = mNextAlarmTextView.getText();

        boolean alarmVisible = false;
        if (mNextAlarm != null) {
            alarmVisible = true;
            mNextAlarmTextView.setText(formatNextAlarm(mNextAlarm));
        }
        mNextAlarmIcon.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmTextView.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmContainer.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != alarmVisible ||
                !Objects.equals(originalAlarmText, mNextAlarmTextView.getText());
    }

    private void applyDarkness(int id, Rect tintArea, float intensity, int color) {
        View v = findViewById(id);
        if (v instanceof DarkReceiver) {
            ((DarkReceiver) v).onDarkChanged(tintArea, intensity, color);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        updateResources();
        updateStatusbarProperties();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    /**
     * The height of QQS should always be the status bar height + 128dp. This is normally easy, but
     * when there is a notch involved the status bar can remain a fixed pixel size.
     */
    private void updateMinimumHeight() {
        int sbHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        int qqsHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.qs_quick_header_panel_height);
        if (mIsQuickQsBrightnessEnabled) {
            qqsHeight += mContext.getResources().getDimensionPixelSize(
                    R.dimen.brightness_mirror_height)
                    + mContext.getResources().getDimensionPixelSize(
                    R.dimen.qs_tile_margin_top);
        }
        setMinimumHeight(sbHeight + qqsHeight);
    }

    private void updateResources() {
        Resources resources = mContext.getResources();
        updateMinimumHeight();

        // Update height for a few views, especially due to landscape mode restricting space.
        mHeaderTextContainerView.getLayoutParams().height =
                resources.getDimensionPixelSize(R.dimen.qs_header_tooltip_height);
        mHeaderTextContainerView.setLayoutParams(mHeaderTextContainerView.getLayoutParams());

        int topMargin = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height) + (mHeaderImageEnabled ?
                resources.getDimensionPixelSize(R.dimen.qs_header_image_offset) : 0);

        mSystemIconsView.getLayoutParams().height = topMargin;
        mSystemIconsView.setLayoutParams(mSystemIconsView.getLayoutParams());

        if (mIsQuickQsBrightnessEnabled) {
            // Offset container margin to align quick QS brightness bar with QS brightness bar.
            RelativeLayout.LayoutParams lpQuickQsBrightness = (RelativeLayout.LayoutParams)
                    mQuickQsBrightness.getLayoutParams();
            lpQuickQsBrightness.setMargins(
                    resources.getDimensionPixelSize(R.dimen.notification_side_paddings)
                            - resources.getDimensionPixelSize(R.dimen.status_bar_padding_start),
                    0, resources.getDimensionPixelSize(R.dimen.notification_side_paddings)
                            - resources.getDimensionPixelSize(R.dimen.status_bar_padding_end),
                    0);
            mQuickQsBrightness.setLayoutParams(lpQuickQsBrightness);
            if (mIsQsAutoBrightnessEnabled && resources.getBoolean(
                    com.android.internal.R.bool.config_automatic_brightness_available)) {
                mQuickQsBrightness.findViewById(R.id.brightness_icon).setVisibility(View.VISIBLE);
            } else {
                mQuickQsBrightness.findViewById(R.id.brightness_icon).setVisibility(View.GONE);
            }
            mQuickQsBrightness.setVisibility(View.VISIBLE);
        } else {
            mQuickQsBrightness.setVisibility(View.GONE);
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (mQsDisabled) {
            lp.height = topMargin;
        } else {
            int qsHeight = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.quick_qs_total_height);

            if (mHeaderImageEnabled) {
                qsHeight += resources.getDimensionPixelSize(R.dimen.qs_header_image_offset);
            }
            lp.height = Math.max(getMinimumHeight(), qsHeight);
        }

        if (isAlwaysShowSettings) {
            lp.height += 30;
        }

        setLayoutParams(lp);

        updateStatusIconAlphaAnimator();
        updateHeaderTextContainerAlphaAnimator();
        updatePrivacyChipAlphaAnimator();
    }

    private void updateSettings() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        isAlwaysShowSettings = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_ALWAYS_SHOW_SETTINGS, 0,
                UserHandle.USER_CURRENT) == 1;
        updateResources();
        updateStatusbarProperties();
        mSystemInfoMode = getQsSystemInfoMode();
        mQSBatteryMode = getQSBatteryMode();
        mSysBatteryMode = getSysBatteryMode();
        setQSBatteryVisibility();
        setSysBatteryVisibility();
        updateSystemInfoText();
        updateQsBrightness();
    }

    private int getQsBrightnessMode() {
        return Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_SHOW_BRIGHTNESS_SLIDER_EXTEND, 0,
                UserHandle.USER_CURRENT);
    }

    private void updateQsBrightness() {
        switch(getQsBrightnessMode()) {
        case 0:
            mIsQuickQsBrightnessEnabled = false;
            mIsQsAutoBrightnessEnabled = false;
            updateResources();
            break;
        case 1:
            mIsQuickQsBrightnessEnabled = true;
            mIsQsAutoBrightnessEnabled = false;
            updateResources();
            break;
        case 2:
            mIsQuickQsBrightnessEnabled = true;
            mIsQsAutoBrightnessEnabled = true;
            updateResources();
            break;
        }
    }

    private void updateBatteryStyle() {
        mBatteryRemainingIcon.mBatteryStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0);
        mBatteryRemainingIcon.updateBatteryStyle();
        mBatteryRemainingIcon.updatePercentView();
        mBatteryRemainingIcon.updateVisibility();
    }

    private void updateStatusIconAlphaAnimator() {
        mStatusIconsAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mQuickQsStatusIcons, "alpha", 1, 0, 0)
                .build();
    }

    private void updateHeaderTextContainerAlphaAnimator() {
        mHeaderTextContainerAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mHeaderTextContainerView, "alpha", 0, 0, 1)
                .build();
    }

    private void updatePrivacyChipAlphaAnimator() {
        mPrivacyChipAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mPrivacyChip, "alpha", 1, 0, 1)
                .build();
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mHeaderQsPanel.setExpanded(expanded);
        updateSystemInfoText();
        updateEverything();
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;
        if (mStatusIconsAlphaAnimator != null) {
            mStatusIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }

        if (forceExpanded) {
            // If the keyguard is showing, we want to offset the text so that it comes in at the
            // same time as the panel as it slides down.
            mHeaderTextContainerView.setTranslationY(panelTranslationY);
        } else {
            mHeaderTextContainerView.setTranslationY(0f);
        }

        if (mHeaderTextContainerAlphaAnimator != null) {
            mHeaderTextContainerAlphaAnimator.setPosition(keyguardExpansionFraction);
            if (keyguardExpansionFraction > 0) {
                mHeaderTextContainerView.setVisibility(VISIBLE);
            } else {
                mHeaderTextContainerView.setVisibility(INVISIBLE);
            }
        }
        if (mPrivacyChipAlphaAnimator != null) {
            mPrivacyChip.setExpanded(expansionFraction > 0.5);
            mPrivacyChipAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mIsQuickQsBrightnessEnabled) {
            if (keyguardExpansionFraction > 0) {
                mQuickQsBrightness.setVisibility(INVISIBLE);
            } else {
                mQuickQsBrightness.setVisibility(VISIBLE);
            }
        }
        updateSystemInfoText();
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mHeaderTextContainerView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mQuickQsStatusIcons.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mQuickQsBrightness.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarIconController.addIconGroup(mIconManager);
        requestApplyInsets();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        DisplayCutout cutout = insets.getDisplayCutout();
        Pair<Integer, Integer> padding = PhoneStatusBarView.cornerCutoutMargins(
                cutout, getDisplay());
        int paddingStart = getResources().getDimensionPixelSize(R.dimen.status_bar_padding_start);
        int paddingTop = getResources().getDimensionPixelSize(R.dimen.status_bar_padding_top);
        int paddingEnd = getResources().getDimensionPixelSize(R.dimen.status_bar_padding_end);
        if (padding == null) {
            mSystemIconsView.setPaddingRelative(
                    paddingStart,
                    paddingTop,
                    paddingEnd,
                    0);
        } else {
            mSystemIconsView.setPadding(
                    Math.max(paddingStart, padding.first),
                    paddingTop,
                    Math.max(paddingEnd, padding.second),
                    0);
        }



        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mSpace.getLayoutParams();
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty()) {
                mHasTopCutout = false;
                lp.width = 0;
                mSpace.setVisibility(View.GONE);
            } else {
                mHasTopCutout = true;
                lp.width = topCutout.width();
                mSpace.setVisibility(View.VISIBLE);
            }
        }
        mSpace.setLayoutParams(lp);
        setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
        return super.onApplyWindowInsets(insets);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        setListening(false);
        mStatusBarIconController.removeIconGroup(mIconManager);
        super.onDetachedFromWindow();
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mHeaderQsPanel.setListening(listening);
        mListening = listening;
        mCarrierGroup.setListening(mListening);

        if (listening) {
            mZenController.addCallback(this);
            mAlarmController.addCallback(this);
            mContext.registerReceiver(mRingerReceiver,
                    new IntentFilter(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION));
            mPrivacyItemController.addCallback(mPICCallback);
            mBrightnessController.registerCallbacks();
        } else {
            mZenController.removeCallback(this);
            mAlarmController.removeCallback(this);
            mPrivacyItemController.removeCallback(mPICCallback);
            mContext.unregisterReceiver(mRingerReceiver);
            mBrightnessController.unregisterCallbacks();
            mPrivacyChipLogged = false;
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mClockView || v == mNextAlarmTextView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mNextAlarmContainer && mNextAlarmContainer.isVisibleToUser()) {
            if (mNextAlarm.getShowIntent() != null) {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        mNextAlarm.getShowIntent());
            } else {
                Log.d(TAG, "No PendingIntent for next alarm. Using default intent");
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            }
        } else if (v == mPrivacyChip) {
            // Makes sure that the builder is grabbed as soon as the chip is pressed
            PrivacyDialogBuilder builder = mPrivacyChip.getBuilder();
            if (builder.getAppsAndTypes().size() == 0) return;
            Handler mUiHandler = new Handler(Looper.getMainLooper());
            StatsLog.write(StatsLog.PRIVACY_INDICATORS_INTERACTED,
                    StatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__CHIP_CLICKED);
            mUiHandler.post(() -> {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        new Intent(Intent.ACTION_REVIEW_ONGOING_PERMISSION_USAGE), 0);
                mHost.collapsePanels();
            });
        } else if (v == mRingerContainer && mRingerContainer.isVisibleToUser()) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Settings.ACTION_SOUND_SETTINGS), 0);
        } else if (v == mDateView) {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
        } else if (v == mBatteryRemainingIcon || v == mBatteryMeterView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                Intent.ACTION_POWER_USAGE_SUMMARY), 0);
        }
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        updateStatusText();
    }

    @Override
    public void onZenChanged(int zen) {
        updateStatusText();
    }

    @Override
    public void onConfigChanged(ZenModeConfig config) {
        updateStatusText();
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        setupHost(qsPanel.getHost());
    }

    public void setupHost(final QSTileHost host) {
        mHost = host;
        //host.setHeaderView(mExpandIndicator);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host, null /* No customization in header */);


        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);
        mBatteryRemainingIcon.onDarkChanged(tintArea, intensity, fillColor);

        // Use SystemUI context to get battery meter colors, and let it use the default tint (white)
        mBatteryMeterView.setColorsFromContext(mHost.getContext());
        mBatteryMeterView.onDarkChanged(new Rect(), 0, DarkIconDispatcher.DEFAULT_ICON_TINT);
        if(mSystemInfoText != null &&  mSystemInfoIcon != null) {
            updateSystemInfoText();
        }
    }

    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    private String formatNextAlarm(AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = android.text.format.DateFormat
                .is24HourFormat(mContext, ActivityManager.getCurrentUser()) ? "EHm" : "Ehma";
        String pattern = android.text.format.DateFormat
                .getBestDateTimePattern(Locale.getDefault(), skeleton);
        return android.text.format.DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    public static float getColorIntensity(@ColorInt int color) {
        return color == Color.WHITE ? 0 : 1;
    }

    public void setMargins(int sideMargins) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            // Prevents these views from getting set a margin.
            // The Icon views all have the same padding set in XML to be aligned.
            if (v == mSystemIconsView || v == mQuickQsStatusIcons || v == mHeaderQsPanel
                    || v == mHeaderTextContainerView) {
                continue;
            }
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
            lp.leftMargin = sideMargins;
            lp.rightMargin = sideMargins;
        }
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_HEADER), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_SYSTEM_INFO), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_BATTERY_MODE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_SYS_BATTERY_MODE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_SYSTEM_INFO_ICON), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_ALWAYS_SHOW_SETTINGS), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_SHOW_BRIGHTNESS_SLIDER_EXTEND), false,
                    this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            updateSystemInfoText();
            setQSBatteryVisibility();
            setSysBatteryVisibility();
        }
    }

    // Update color schemes in landscape to use wallpaperTextColor
    private void updateStatusbarProperties() {
        boolean shouldUseWallpaperTextColor = (mLandscape || mForceHideQsStatusBar) && !mHeaderImageEnabled;
        mBatteryMeterView.useWallpaperTextColor(shouldUseWallpaperTextColor);
        mClockView.useWallpaperTextColor(shouldUseWallpaperTextColor);
    }
}