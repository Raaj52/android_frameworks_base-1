/*
* Copyright (C) 2019 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.zenx;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

import androidx.palette.graphics.Palette;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";

    private ValueAnimator mLightAnimator;
    private ImageView mLeftViewSolid;
    private ImageView mLeftViewFaded;
    private ImageView mRightViewSolid;
    private ImageView mRightViewFaded;
    private AnimatorUpdateListener mAnimatorUpdateListener = new AnimatorUpdateListener() {
        public void onAnimationUpdate(ValueAnimator animation) {
            if (DEBUG) Log.d(TAG, "onAnimationUpdate");
            float progress = ((Float) animation.getAnimatedValue()).floatValue();
            mLeftViewSolid.setScaleY(progress);
            mLeftViewFaded.setScaleY(progress);
            mRightViewSolid.setScaleY(progress);
            mRightViewFaded.setScaleY(progress);
            float alpha = 1.0f;
            if (progress <= 0.3f) {
                alpha = progress / 0.3f;
            } else if (progress >= 1.0f) {
                alpha = 2.0f - progress;
            }
            mLeftViewSolid.setAlpha(alpha);
            mLeftViewFaded.setAlpha(alpha);
            mRightViewSolid.setAlpha(alpha);
            mRightViewFaded.setAlpha(alpha);
        }
    };

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (DEBUG) Log.d(TAG, "new");
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
    }

    public void animateNotification() {
        animateNotificationWithColor(getNotificationLightsColor());
    }

    public int getNotificationLightsColor() {
        int color = getDefaultNotificationLightsColor();
        int lightColor = getlightColor();
        int customColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_CUSTOM_COLOR, getDefaultNotificationLightsColor(),
                UserHandle.USER_CURRENT);
        if (lightColor == 1) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                WallpaperInfo wallpaperInfo = wallpaperManager.getWallpaperInfo();
                if (wallpaperInfo == null) { // if not a live wallpaper
                    Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                    Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                    if (bitmap != null) { // if wallpaper is not blank
                        Palette p = Palette.from(bitmap).generate();
                        int wallColor = p.getDominantColor(color);
                        if (color != wallColor)
                            color = wallColor;
                    }
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else if (lightColor == 2) {
            color = Utils.getColorAccentDefaultColor(getContext());
        } else if (lightColor == 3) {
            color = customColor;
        } else if (lightColor == 4) {
            color = randomColor();
        }  else {
            color = 0xFFFFFFFF;
        }
        return color;
    }

    public int getDefaultNotificationLightsColor() {
        return getResources().getInteger(com.android.internal.R.integer.config_AmbientPulseLightColor);
    }

    public void endAnimation() {
        mLightAnimator.end();
        mLightAnimator.removeAllUpdateListeners();
    }

    public int randomColor() {
        int red = (int) (0xff * Math.random());
        int green = (int) (0xff * Math.random());
        int blue = (int) (0xff * Math.random());
        return Color.argb(255, red, green, blue);
    }

    public int getlightColor() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_COLOR, 0,
                UserHandle.USER_CURRENT);
    }

    public void animateNotificationWithColor(int color) {
        int layout = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_LAYOUT, 0,
                UserHandle.USER_CURRENT);
        int lightcolor = getlightColor();
        if (mLeftViewSolid == null) {
            mLeftViewSolid = (ImageView) findViewById(R.id.notification_animation_left_solid);
        }
        if (mLeftViewFaded == null) {
            mLeftViewFaded = (ImageView) findViewById(R.id.notification_animation_left_faded);
        }
        if (mRightViewSolid == null) {
            mRightViewSolid = (ImageView) findViewById(R.id.notification_animation_right_solid);
        }
        if (mRightViewFaded == null) {
            mRightViewFaded = (ImageView) findViewById(R.id.notification_animation_right_faded);
        }
        if (lightcolor == 5) {
            mLeftViewSolid.setColorFilter(randomColor());
            mLeftViewFaded.setColorFilter(randomColor());
        } else {
            mLeftViewSolid.setColorFilter(color);
            mLeftViewFaded.setColorFilter(color);
        }
        mLeftViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        mLeftViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        if (lightcolor == 5) {
            mRightViewSolid.setColorFilter(randomColor());
            mRightViewFaded.setColorFilter(randomColor());
        } else {
           mRightViewSolid.setColorFilter(color);
           mRightViewFaded.setColorFilter(color);
        }
        mRightViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        mRightViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        if (!mLightAnimator.isRunning()) {
            if (DEBUG) Log.d(TAG, "start");
            int repeat = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.AMBIENT_LIGHT_REPEAT_COUNT, 0,
                    UserHandle.USER_CURRENT);
            int duration = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.AMBIENT_LIGHT_DURATION, 2,
                    UserHandle.USER_CURRENT) * 1000;
            boolean directionIsRestart = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.AMBIENT_LIGHT_REPEAT_DIRECTION, 0,
                    UserHandle.USER_CURRENT) != 1;
            mLightAnimator.setDuration(duration);
            mLightAnimator.setRepeatMode(directionIsRestart ? ValueAnimator.RESTART : ValueAnimator.REVERSE);
            if (repeat == 0) {
                mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
            } else {
                mLightAnimator.setRepeatCount(repeat - 1);
            }
            mLightAnimator.addUpdateListener(mAnimatorUpdateListener);
            mLightAnimator.start();
        }
    }
}
