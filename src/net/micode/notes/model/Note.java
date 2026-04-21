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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记实体类，封装了单条笔记的属性和数据操作。
 * 包含笔记的基本信息（标题、内容、时间等）以及关联的文本数据、通话记录数据。
 * 负责将内存中的笔记变更同步到数据库（ContentProvider）。
 */
public class Note {
    // 存储笔记基本字段的修改值（对应 NoteColumns 中的列）
    private ContentValues mNoteDiffValues;
    // 笔记的详细数据对象（包含文本数据和通话数据）
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 创建一个新的笔记ID（在数据库中插入一条空白笔记记录）
     * @param context  上下文对象，用于访问 ContentResolver
     * @param folderId 所属文件夹的ID（父目录ID）
     * @return 新创建的笔记ID，若失败返回0
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 准备新笔记的初始值
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);   // 创建时间
        values.put(NoteColumns.MODIFIED_DATE, createdTime);  // 修改时间（初始同创建时间）
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);       // 类型：普通笔记
        values.put(NoteColumns.LOCAL_MODIFIED, 1);           // 本地已修改标记（1表示有未同步修改）
        values.put(NoteColumns.PARENT_ID, folderId);         // 所属文件夹ID

        // 通过 ContentProvider 插入笔记记录，得到返回的 Uri
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // Uri 格式：content://.../note/ID，取最后一段作为ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "获取笔记ID出错 :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("错误的笔记ID:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造函数：初始化笔记的修改值容器和详细数据对象
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记基本字段的值（例如标题、内容摘要等）
     * 同时自动标记 LOCAL_MODIFIED 和 MODIFIED_DATE 为当前时间
     * @param key   字段名（如 NoteColumns.TITLE）
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置笔记的文本数据（实际是笔记的详细文本内容）
     * @param key   数据字段名（如 DataColumns.CONTENT）
     * @param value 文本内容
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据在数据库中的ID（用于更新已有数据）
     * @param id 文本数据的记录ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据的记录ID
     * @return 文本数据ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据的ID
     * @param id 通话数据记录ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置笔记的通话记录数据（例如电话号码、通话时长等）
     * @param key   字段名
     * @param value 字段值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断笔记是否有本地未同步的修改
     * @return true 表示有修改（基本字段或详细数据有变动）
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将笔记的所有本地修改同步到数据库（ContentProvider）
     * @param context 上下文
     * @param noteId  笔记ID（必须大于0）
     * @return true 同步成功，false 失败
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("错误的笔记ID:" + noteId);
        }

        // 如果没有本地修改，直接返回成功
        if (!isLocalModified()) {
            return true;
        }

        /**
         * 理论上，数据一旦变化，笔记的 LOCAL_MODIFIED 和 MODIFIED_DATE 就应该更新。
         * 即使更新笔记基本信息失败，我们仍然尝试更新详细数据，保证数据安全。
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "更新笔记基本信息失败，这不应该发生");
            // 不直接返回，继续执行后续详细数据的同步
        }
        mNoteDiffValues.clear();  // 清除已同步的基本字段修改

        // 同步详细数据（文本数据、通话数据），如果失败则返回 false
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类：笔记的详细数据（文本内容和通话记录数据）
     * 每个笔记可以包含零个或一个文本数据，零个或一个通话数据
     */
    private class NoteData {
        private long mTextDataId;          // 文本数据在数据库中的ID（0表示尚未插入）
        private ContentValues mTextDataValues;   // 待更新的文本数据字段

        private long mCallDataId;          // 通话数据在数据库中的ID（0表示尚未插入）
        private ContentValues mCallDataValues;   // 待更新的通话数据字段

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断详细数据是否有本地修改
         * @return true 表示文本或通话数据有改动
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据ID（用于更新已存在的文本记录）
         * @param id 必须大于0
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("文本数据ID必须大于0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据ID（用于更新已存在的通话记录）
         * @param id 必须大于0
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("通话数据ID必须大于0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据的字段值，并标记笔记整体已修改
         * @param key   字段名
         * @param value 字段值
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据的字段值，并标记笔记整体已修改
         * @param key   字段名
         * @param value 字段值
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将详细数据（文本/通话）推送到 ContentProvider
         * @param context 上下文
         * @param noteId  所属笔记ID
         * @return 成功返回笔记的 Uri，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("错误的笔记ID:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);   // 关联笔记ID
                if (mTextDataId == 0) {
                    // 没有ID → 插入新记录
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入文本数据失败，笔记ID:" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 已有ID → 更新记录
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();  // 清除已处理的修改
            }

            // 处理通话数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 插入新通话记录
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入通话数据失败，笔记ID:" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有通话记录
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 执行批量操作（如果存在需要更新的记录）
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}