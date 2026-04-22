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

package net.micode.notes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 数据库帮助类，负责创建和管理笔记应用的 SQLite 数据库。
 * 包含 note 表（存储笔记/文件夹元数据）和 data 表（存储笔记详细内容）。
 * 同时定义了大量触发器（Trigger）维护数据一致性，如文件夹笔记计数、摘要同步、级联删除等。
 * 支持从版本 1 到版本 5 的数据库升级。
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";
    // 数据库版本（当前为 5）
    private static final int DB_VERSION = 5;

    /**
     * 表名常量接口
     */
    public interface TABLE {
        public static final String NOTE = "note";   // 笔记/文件夹元数据表
        public static final String DATA = "data";   // 笔记内容数据表
    }

    private static final String TAG = "NotesDatabaseHelper";
    private static NotesDatabaseHelper mInstance;   // 单例实例

    // ======================= 创建 note 表的 SQL 语句 =======================
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +                    // 主键ID
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +      // 父文件夹ID，0表示根目录
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +   // 提醒日期
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +    // 背景颜色ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 创建时间（毫秒）
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +  // 是否有附件
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +     // 文件夹内笔记数量（仅对文件夹有效）
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +           // 摘要（用于列表显示）
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +            // 类型：笔记/文件夹/系统
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +       // 桌面小部件ID
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +    // 小部件类型
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +         // 同步ID
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +  // 本地修改标记（用于同步）
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +// 原始父文件夹ID（用于移动后还原）
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +          // Google Tasks 的ID
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0," +         // 版本号（用于冲突检测）
            NoteColumns.FAVORITE + " INTEGER NOT NULL DEFAULT 0" +         // 收藏标记（1收藏，0未收藏）
        ")";

    // ======================= 创建 data 表的 SQL 语句 =======================
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +           // 主键ID
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +          // MIME类型（区分文本、通话记录等）
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," + // 所属笔记ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +  // 内容文本
            DataColumns.DATA1 + " INTEGER," +                     // 备用整数字段1
            DataColumns.DATA2 + " INTEGER," +                     // 备用整数字段2
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +    // 备用文本字段3
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +    // 备用文本字段4
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +     // 备用文本字段5
        ")";

    // 在 data 表的 NOTE_ID 列上创建索引，加速按笔记ID查询
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ======================= 触发器（Triggers）维护数据一致性 =======================

    /**
     * 当笔记被移动到另一个文件夹时，增加新文件夹的笔记计数
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 当笔记被移出某个文件夹时，减少原文件夹的笔记计数
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * 当向文件夹中插入新笔记时，增加文件夹的笔记计数
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 当从文件夹中删除笔记时，减少文件夹的笔记计数
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * 当插入笔记内容（MIME_TYPE 为 NOTE）时，自动更新 note 表的 SNIPPET 字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当更新笔记内容时，同步更新 note 表的 SNIPPET
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当删除笔记内容时，将对应 note 的 SNIPPET 设为空字符串
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当删除一条笔记时，级联删除其所有数据记录（data 表中的内容）
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 当删除一个文件夹时，级联删除该文件夹下的所有笔记（或子文件夹）
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 当文件夹被移动到回收站时，将其下所有笔记也移动到回收站
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 构造函数（私有，通过单例模式获取实例）
     * @param context 上下文
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建 note 表，并重建相关触发器，初始化系统文件夹
     * @param db 可写的数据库实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重建 note 表的所有触发器（先删除旧的，再创建新的）
     * @param db 数据库实例
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除可能存在的旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新的触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 创建系统文件夹（通话记录文件夹、根文件夹、临时文件夹、回收站）
     * @param db 数据库实例
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        // 通话记录文件夹
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 根文件夹（默认文件夹）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 临时文件夹（用于移动笔记时的中转）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 回收站文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建 data 表，重建相关触发器，并创建索引
     * @param db 数据库实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重建 data 表的所有触发器（先删除旧的，再创建新的）
     * @param db 数据库实例
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取单例实例（线程安全）
     * @param context 上下文
     * @return NotesDatabaseHelper 单例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 数据库版本升级逻辑
     * 支持从版本 1 逐步升级到当前版本（5）
     * @param db         数据库实例
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // 从版本 1 升级到版本 2（重建所有表）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // 本次升级包含了 v2→v3 的部分，避免重复
            oldVersion++;
        }

        // 从版本 2 升级到版本 3（添加 GTASK_ID 列和回收站文件夹）
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true; // 需要重建触发器
            oldVersion++;
        }

        // 从版本 3 升级到版本 4（添加 VERSION 列）
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 从版本 4 升级到版本 5（添加 FAVORITE 列）
        if (oldVersion == 4) {
            upgradeToV5(db);
            oldVersion++;
        }

        // 如果之前标记需要重建触发器（版本2→3时），重新创建所有触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 如果升级后版本号仍不匹配，说明升级失败
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级到版本 2：完全重建 note 和 data 表（清空所有数据）
     * @param db 数据库实例
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到版本 3：
     * - 删除无用的旧触发器
     * - 为 note 表添加 GTASK_ID 列
     * - 插入回收站系统文件夹
     * @param db 数据库实例
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除不再使用的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加 Google Tasks 关联ID列
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 创建回收站文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到版本 4：为 note 表添加 VERSION 列（用于冲突检测）
     * @param db 数据库实例
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 升级到版本 5：为 note 表添加 FAVORITE 列（收藏标记）
     * @param db 数据库实例
     */
    private void upgradeToV5(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.FAVORITE
                + " INTEGER NOT NULL DEFAULT 0");
    }
}