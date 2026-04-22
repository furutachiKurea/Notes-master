/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 闹钟警报Activity - 当笔记闹钟触发时显示警报对话框
 * 功能：
 * 1. 显示闹钟提醒对话框，展示笔记内容
 * 2. 播放闹钟铃声
 * 3. 用户可以选择打开笔记或直接关闭警报
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    /** 笔记ID */
    private long mNoteId;
    /** 笔记摘要（预览文本） */
    private String mSnippet;
    /** 摘要预览的最大长度 */
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    /** 媒体播放器，用于播放闹钟铃声 */
    /** MediaPlayer */
    MediaPlayer mPlayer;

    /**
     * Activity创建时的初始化方法
     * 1. 设置窗口属性（无标题栏、锁屏时显示）
     * 2. 如果屏幕未点亮，则点亮屏幕并保持唤醒
     * 3. 获取闹钟触发的笔记ID和内容
     * 4. 显示警报对话框并播放铃声
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕未点亮，则添加标志点亮屏幕并保持唤醒状态
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            // 从Intent中解析笔记ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 获取笔记的摘要内容
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            // 如果摘要过长，截取前60个字符并添加省略号
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        // 检查笔记是否存在于数据库中
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();
            playAlarmSound();
        } else {
            finish();
        }
    }

    /**
     * 检查屏幕是否已点亮
     * @return 屏幕已点亮返回true，否则返回false
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟铃声
     * 1. 获取系统默认的闹钟铃声
     * 2. 检查是否在静音模式下
     * 3. 设置音频流类型
     * 4. 准备和播放媒体文件，并设置循环播放
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟铃声URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取静音模式影响的音频流
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 根据静音模式设置音频流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            // 设置铃声循环播放
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示闹钟警报对话框
     * 1. 创建AlertDialog显示笔记摘要
     * 2. 添加"确定"按钮
     * 3. 如果屏幕已点亮，添加"打开"按钮以编辑笔记
     */
    /**
     * 显示闹钟警报对话框
     * 1. 创建AlertDialog显示笔记摘要
     * 2. 添加"确定"按钮
     * 3. 如果屏幕已点亮，添加"打开"按钮以编辑笔记
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);
        if (isScreenOn()) {
            // 屏幕已点亮时，允许用户直接打开笔记编辑
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击事件处理
     * @param dialog 对话框对象
     * @param which 被点击的按钮ID
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 用户点击"打开"按钮，启动笔记编辑Activity
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /**
     * 对话框关闭事件处理
     * 停止播放铃声并关闭Activity
     * @param dialog 对话框对象
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止播放闹钟铃声并释放MediaPlayer资源
     */
    /**
     * 停止播放闹钟铃声并释放MediaPlayer资源
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            // 停止播放
            mPlayer.stop();
            // 释放资源
            mPlayer.release();
            // 置空引用
            mPlayer = null;
        }
    }
}
