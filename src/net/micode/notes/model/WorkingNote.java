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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 工作笔记类，封装了当前正在编辑或显示的笔记的完整信息。
 * 它内部持有一个 Note 对象用于数据同步，并提供了对笔记内容、提醒时间、
 * 背景颜色、小部件属性等的高层操作接口。同时支持监听器模式，当笔记属性
 * 变化时通知 UI 更新。
 */
public class WorkingNote {
    // 底层数据操作对象（负责与 ContentProvider 交互）
    private Note mNote;
    // 笔记 ID（若 >0 表示已存在于数据库）
    private long mNoteId;
    // 笔记的文本内容（摘要或全部）
    private String mContent;
    // 笔记模式（普通文本模式或 checklist 模式）
    private int mMode;

    private long mAlertDate;          // 提醒时间（毫秒时间戳）
    private long mModifiedDate;       // 最后修改时间

    private int mBgColorId;           // 背景颜色资源 ID
    private int mWidgetId;            // 关联的桌面小部件 ID
    private int mWidgetType;          // 小部件类型
    private long mFolderId;           // 所属文件夹 ID

    private Context mContext;         // 上下文

    private static final String TAG = "WorkingNote";

    private boolean mIsDeleted;       // 是否已标记为删除

    private NoteSettingChangedListener mNoteSettingStatusListener;  // 属性变化监听器

    // 查询 data 表时需要的列投影
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询 note 表时需要的列投影
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // DATA_PROJECTION 中各列的索引常量
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;   // DATA1 字段存储模式

    // NOTE_PROJECTION 中各列的索引常量
    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    /**
     * 私有构造方法：用于创建全新的笔记（尚不存在于数据库）
     * @param context  上下文
     * @param folderId 所属文件夹 ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;            // 新笔记 ID 为 0
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;   // 无效的小部件类型
    }

    /**
     * 私有构造方法：用于加载已存在的笔记
     * @param context  上下文
     * @param noteId   笔记 ID
     * @param folderId 所属文件夹 ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();     // 从数据库加载笔记信息
    }

    /**
     * 从数据库加载笔记的基本信息（来自 note 表）
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        // 再加载笔记的详细数据（来自 data 表）
        loadNoteData();
    }

    /**
     * 加载笔记的详细数据：文本内容、模式以及通话记录关联数据等
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        // 普通笔记内容
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 通话记录笔记
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 工厂方法：创建一个空的笔记（新笔记）
     * @param context          上下文
     * @param folderId         所属文件夹 ID
     * @param widgetId         小部件 ID
     * @param widgetType       小部件类型
     * @param defaultBgColorId 默认背景颜色 ID
     * @return 新创建的 WorkingNote 实例
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 工厂方法：加载已存在的笔记
     * @param context 上下文
     * @param id      笔记 ID
     * @return WorkingNote 实例
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存笔记到数据库（同步）
     * @return true 表示保存成功，false 表示无需保存或保存失败
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            // 如果笔记尚不存在于数据库，则先创建一条新记录获取 ID
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 同步笔记基本信息和详细数据
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果该笔记关联了有效的桌面小部件，则通知监听器更新小部件内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断笔记是否已存在于数据库中
     * @return true 表示已存在（mNoteId > 0）
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断当前笔记状态是否值得保存（有实际更改且未被删除）
     * @return true 表示值得保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记属性变化的监听器
     * @param l 监听器实例
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒时间
     * @param date 提醒的毫秒时间戳（0 表示取消提醒）
     * @param set  true 表示设置提醒，false 表示取消（用于回调参数）
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记笔记为删除状态（并不立即删除，只是设置标志）
     * @param mark true 标记为已删除，false 取消删除标记
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置笔记背景颜色
     * @param id 背景颜色资源 ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置笔记的模式（普通文本模式或 checklist 模式）
     * @param mode 模式值（0 普通，1 checklist）
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置小部件类型
     * @param type 小部件类型常量
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置小部件 ID
     * @param id 小部件 ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置笔记的文本内容
     * @param text 新的内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将当前笔记转换为通话记录笔记
     * @param phoneNumber 电话号码
     * @param callDate    通话时间
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 判断笔记是否设置了提醒
     * @return true 表示有提醒
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    // ----- 各种 getter 方法 -----
    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 根据背景颜色 ID 获取对应的背景资源 ID
     * @return 资源 ID
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取标题栏背景资源 ID（根据背景颜色）
     * @return 资源 ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 笔记属性变化监听器接口
     * UI 层可以实现此接口以响应笔记背景色、提醒、小部件、模式等变化
     */
    public interface NoteSettingChangedListener {
        /**
         * 背景颜色改变时调用
         */
        void onBackgroundColorChanged();

        /**
         * 提醒时间改变时调用
         * @param date 提醒时间戳
         * @param set  是否设置了提醒
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 小部件内容需要更新时调用（例如笔记内容变化）
         */
        void onWidgetChanged();

        /**
         * 在普通模式和 checklist 模式之间切换时调用
         * @param oldMode 切换前的模式
         * @param newMode 切换后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}