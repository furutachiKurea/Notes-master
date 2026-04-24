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
 * 笔记数据库帮助类
 * 负责创建、升级和管理笔记应用所需的数据库
 * 使用单例模式确保只有一个数据库实例
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";
    // 数据库版本号
    private static final int DB_VERSION = 5;

    /**
     * 数据表名称定义接口
     */
    public interface TABLE {
        public static final String NOTE = "note";  // 笔记表
        public static final String DATA = "data";  // 数据表（存储笔记的具体内容）
    }

    private static final String TAG = "NotesDatabaseHelper";
    private static NotesDatabaseHelper mInstance;  // 单例实例

    /**
     * 创建笔记表的SQL语句
     * 包含笔记的所有元数据字段
     */
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +                    // 笔记ID，主键
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +     // 父文件夹ID，0表示根目录
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +   // 提醒时间
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +    // 背景颜色ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +  // 创建时间（毫秒）
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +  // 是否有附件
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +     // 文件夹内的笔记数量
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +          // 笔记摘要（用于列表预览）
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +           // 笔记类型（普通笔记/文件夹/系统文件夹）
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +       // 桌面小部件ID
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +    // 桌面小部件类型
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +         // 同步ID
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +  // 本地是否已修改
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 原始父文件夹ID（用于同步）
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +          // Google Tasks ID
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0," +         // 版本号
            NoteColumns.FAVORITE + " INTEGER NOT NULL DEFAULT 0" +         // 是否收藏
        ")";

    /**
     * 创建数据表的SQL语句
     * 存储笔记的实际内容，支持多种MIME类型
     */
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +                    // 数据ID，主键
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +                  // MIME类型（如文本、图片、音频等）
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +       // 所属笔记ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +  // 创建时间
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +         // 内容（主内容）
            DataColumns.DATA1 + " INTEGER," +                            // 扩展字段1（整数型）
            DataColumns.DATA2 + " INTEGER," +                            // 扩展字段2（整数型）
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +           // 扩展字段3（文本型）
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +           // 扩展字段4（文本型）
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +            // 扩展字段5（文本型）
        ")";

    /**
     * 为数据表的NOTE_ID字段创建索引
     * 提高通过笔记ID查询数据的性能
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    /**
     * 触发器：将笔记移动到文件夹时，增加目标文件夹的笔记计数
     * 当更新NOTE表的PARENT_ID字段时触发
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
     * 触发器：将笔记移出文件夹时，减少原文件夹的笔记计数
     * 当更新NOTE表的PARENT_ID字段时触发
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
     * 触发器：向文件夹插入新笔记时，增加该文件夹的笔记计数
     * 在INSERT操作后触发
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
     * 触发器：从文件夹删除笔记时，减少该文件夹的笔记计数
     * 在DELETE操作后触发
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
     * 触发器：插入笔记内容时，同步更新笔记摘要字段
     * 当插入MIME类型为NOTE的数据时，将内容同步到NOTE表的SNIPPET字段
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
     * 触发器：更新笔记内容时，同步更新笔记摘要字段
     * 当更新MIME类型为NOTE的数据时触发
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
     * 触发器：删除笔记内容时，清空笔记摘要字段
     * 当删除MIME类型为NOTE的数据时触发
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
     * 触发器：删除笔记时，同时删除该笔记的所有数据
     * 级联删除，保证数据完整性
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 触发器：删除文件夹时，同时删除该文件夹下的所有笔记
     * 实现文件夹的级联删除
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 触发器：将文件夹移动到回收站时，同时移动该文件夹下的所有笔记
     * 保持文件夹和子笔记的一致性
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
     * 构造函数
     * @param context 上下文对象
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建笔记表
     * @param db SQLite数据库对象
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);          // 执行建表SQL
        reCreateNoteTableTriggers(db);              // 重新创建触发器
        createSystemFolder(db);                     // 创建系统文件夹
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重新创建笔记表相关的触发器
     * 先删除已存在的触发器，再重新创建
     * @param db SQLite数据库对象
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除可能已存在的触发器
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
     * 创建系统文件夹
     * 包括：通话记录文件夹、根文件夹、临时文件夹、回收站
     * @param db SQLite数据库对象
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        /**
         * 创建通话记录文件夹，用于存储通话记录的笔记
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 创建根文件夹，默认的笔记存储位置
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 创建临时文件夹，用于移动笔记时的临时存储
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 创建回收站文件夹，存储已删除的笔记
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建数据表
     * @param db SQLite数据库对象
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);           // 执行建表SQL
        reCreateDataTableTriggers(db);               // 重新创建触发器
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);   // 创建索引
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重新创建数据表相关的触发器
     * 用于保持笔记摘要与内容的同步
     * @param db SQLite数据库对象
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除可能已存在的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        // 创建新的触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取数据库帮助类实例（单例模式）
     * @param context 上下文对象
     * @return NotesDatabaseHelper实例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    /**
     * 创建数据库时调用
     * @param db SQLite数据库对象
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);  // 创建笔记表
        createDataTable(db);  // 创建数据表
    }

    /**
     * 升级数据库时调用
     * 根据版本号逐步升级，保证数据迁移的安全性
     * @param db SQLite数据库对象
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;  // 是否需要重新创建触发器
        boolean skipV2 = false;            // 是否跳过V2升级

        // 从版本1升级到版本2
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;  // 此升级包含v2到v3的升级
            oldVersion++;
        }

        // 从版本2升级到版本3
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // 从版本3升级到版本4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 从版本4升级到版本5
        if (oldVersion == 4) {
            upgradeToV5(db);
            oldVersion++;
        }

        // 如果需要，重新创建触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 检查升级是否完成到目标版本
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级到版本2
     * 完全重建数据库
     * @param db SQLite数据库对象
     */
    private void upgradeToV2(SQLiteDatabase db) {
        // 删除旧表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        // 创建新表
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到版本3
     * 添加GTASK_ID字段和回收站文件夹
     * @param db SQLite数据库对象
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除未使用的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加GTASK_ID列，用于Google Tasks集成
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 添加回收站系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到版本4
     * 添加VERSION字段用于冲突检测
     * @param db SQLite数据库对象
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 升级到版本5
     * 添加FAVORITE字段支持收藏功能
     * @param db SQLite数据库对象
     */
    private void upgradeToV5(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.FAVORITE
                + " INTEGER NOT NULL DEFAULT 0");
    }
}