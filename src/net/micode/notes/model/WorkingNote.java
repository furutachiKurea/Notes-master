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
 * 编辑界面使用的工作态便签模型。
 * 它缓存当前页面正在编辑的内容和设置项，并把变更先写入 {@link Note}
 * 的差量结构，真正保存时再统一同步到 ContentProvider。
 */
public class WorkingNote {
    // Note 模型对象，用于管理便签的数据
    private Note mNote;
    // 便签的业务数据ID
    private long mNoteId;
    // 便签的文本内容
    private String mContent;
    // 便签的模式（普通文本或清单）
    private int mMode;

    // 提醒时间
    private long mAlertDate;

    // 便签的修改时间
    private long mModifiedDate;

    // 便签的背景颜色ID
    private int mBgColorId;

    // 便签Widget的ID（用于Widget显示）
    private int mWidgetId;

    // 便签Widget的类型（2x或4x类型）
    private int mWidgetType;

    // 便签所属的文件夹ID
    private long mFolderId;

    // 应用上下文
    private Context mContext;

    private static final String TAG = "WorkingNote";
    // 标记便签是否已被删除
    private boolean mIsDeleted;

    // 便签设置变更监听器（例如背景色处改的回调）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // 查询便签数据表（Data表）所需的列
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,           // 数据ID
            DataColumns.CONTENT,      // 内容
            DataColumns.MIME_TYPE,    // 类型（文本或通话记录）
            DataColumns.DATA1,        // 辅助数据1和其他4个
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询便签主表（Note表）所需的列
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,        // 文件夹ID
            NoteColumns.ALERTED_DATE,     // 提醒时间
            NoteColumns.BG_COLOR_ID,      // 背景颜色ID
            NoteColumns.WIDGET_ID,        // WidgetID
            NoteColumns.WIDGET_TYPE,      // Widget类型
            NoteColumns.MODIFIED_DATE     // 修改时间
    };

    // DATA表查询结果中的列索引
    private static final int DATA_ID_COLUMN = 0;           // ID列索引
    private static final int DATA_CONTENT_COLUMN = 1;      // 内容列索引
    private static final int DATA_MIME_TYPE_COLUMN = 2;    // 类型列索引
    private static final int DATA_MODE_COLUMN = 3;         // 模式列索引

    // NOTE表查询结果中的列索引
    private static final int NOTE_PARENT_ID_COLUMN = 0;           // 父业ID列索引
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;        // 提醒时间列索引
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;         // 背景颜色ID列索引
    private static final int NOTE_WIDGET_ID_COLUMN = 3;           // WidgetID列索引
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;         // Widget类型列索引
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;       // 修改时间列索引

    /**
     * 创建一个新的水线贴子对象。
     * @param context 应用上下文
     * @param folderId 新便签所属的文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;                              // 初始化，无提醒
        mModifiedDate = System.currentTimeMillis();  // 设置为当前时间
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;                                 // 水线贴子不会附加ID
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;   // 未分配至Widget
    }

    /**
     * 加载一个已存在数据库中的便签。
     * @param context 应用上下文
     * @param noteId 水线贴子ID
     * @param folderId 文件夹ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;           // 且尝试从数据库加载的有效ID
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();                 // 介绍数据库加载便签
    }

    /**
     * 从数据库加载便签的基本信息。
     * 先从 note 主表恢复便签级属性（背景色、提醒时间等），
     * 然后再到 data 表中加载具体的文本和通话数据。
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
        loadNoteData();
    }

    /**
     * 从数据库加载便签的详细数据。
     * data 表里同时存放文本和通话附加数据，通过 mime type 区分记录类型。
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
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
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

    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存便签到数据库。
     * 保存时先为新便签申请 noteId，然后把 Note 中缓存的所有变更统一同步到数据库。
     * @return 是否保存成功
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                // The row in the note table must exist before NoteData can bind data records to it.
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
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

    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断便签是否值得保存。
     * 空白新建便签、已删除便签，以及没有本地改动的旧便签都不需要落库。
     * @return true表示值得保存
     */
    private boolean isWorthSaving() {
        // Skip empty drafts, deleted notes, and unchanged persisted notes to avoid noisy writes.
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置便签的提醒时间。
     * 这里只更新内存里的待同步字段，真正写库发生在 saveNote()。
     * @param date 提醒时间，为0表示取消提醒
     * @param set 是否是设置提醒
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

    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

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
     * 设置便签的清单模式。
     * 清单模式本质上只是文本 data 上的 mode 字段切换，界面据此决定显示文本或清单。
     * @param mode 模式值
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

    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置便签的工作文本内容。
     * @param text 要设置的文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    // 通话便签会额外记录电话和通话时间，并把父目录切到通话记录文件夹。
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

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

    public interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         */
        void onBackgroundColorChanged();

        /**
         * Called when user set clock
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * Call when user create note from widget
         */
        void onWidgetChanged();

        /**
         * Call when switch between check list mode and normal mode
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
