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

import android.net.Uri;

/**
 * 笔记应用的数据契约类。
 * 定义了 ContentProvider 的授权常量、各种 URI、笔记和数据的列名、
 * 系统文件夹 ID、Intent 额外键名以及子数据类型（文本笔记、通话笔记）的常量。
 * 该类作为应用与 ContentProvider 之间的协议，不包含任何逻辑代码。
 */
public class Notes {
    // ContentProvider 的授权字符串，用于唯一标识本应用的数据提供者
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";

    // 笔记类型常量
    public static final int TYPE_NOTE     = 0;   // 普通笔记
    public static final int TYPE_FOLDER   = 1;   // 文件夹
    public static final int TYPE_SYSTEM   = 2;   // 系统文件夹（不可删除）

    /**
     * 以下为系统文件夹的 ID 定义
     * {@link Notes#ID_ROOT_FOLDER }     根文件夹（默认文件夹）
     * {@link Notes#ID_TEMPARAY_FOLDER } 临时文件夹，用于存放未指定文件夹的笔记
     * {@link Notes#ID_CALL_RECORD_FOLDER} 通话记录专用文件夹
     * {@link Notes#ID_TRASH_FOLER }     回收站文件夹
     */
    public static final int ID_ROOT_FOLDER = 0;      // 根文件夹 ID
    public static final int ID_TEMPARAY_FOLDER = -1; // 临时文件夹 ID
    public static final int ID_CALL_RECORD_FOLDER = -2; // 通话记录文件夹 ID
    public static final int ID_TRASH_FOLER = -3;     // 回收站文件夹 ID

    // 用于 Intent 传递数据的额外键名
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 桌面小部件类型常量
    public static final int TYPE_WIDGET_INVALIDE      = -1; // 无效小部件
    public static final int TYPE_WIDGET_2X            = 0;  // 2x2 小部件
    public static final int TYPE_WIDGET_4X            = 1;  // 4x4 小部件

    /**
     * 数据类型常量内部类
     * 定义了笔记应用支持的两种 MIME 类型：普通文本笔记和通话记录笔记
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;      // 文本笔记类型
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE; // 通话笔记类型
    }

    /**
     * 查询所有笔记和文件夹的 URI
     * 对应数据库的 note 表
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询详细数据（文本内容、通话记录等）的 URI
     * 对应数据库的 data 表
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * note 表的列名定义接口
     * 每一列对应笔记或文件夹的元数据字段
     */
    public interface NoteColumns {
        /**
         * 记录的唯一 ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 父文件夹 ID（指向所属文件夹）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 创建时间（毫秒时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间（毫秒时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒时间（毫秒时间戳，0 表示无提醒）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 摘要（文件夹名称或笔记内容的前缀）
         * <P> 数据类型: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * 笔记关联的桌面小部件 ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 笔记关联的桌面小部件类型
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 笔记背景颜色 ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 是否有附件（多媒体笔记至少有一个附件）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹内的笔记数量（仅对文件夹有效）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 类型：笔记(0)、文件夹(1)、系统文件夹(2)
         * <P> 数据类型: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * 最后一次同步的 ID（用于增量同步）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 本地是否被修改（用于同步标记，1 表示已修改未同步）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移动到临时文件夹前的原始父文件夹 ID（用于恢复）
         * <P> 数据类型 : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Tasks 的任务 ID（用于与 GTask 同步）
         * <P> 数据类型 : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 版本号（用于冲突检测）
         * <P> 数据类型 : INTEGER (long) </P>
         */
        public static final String VERSION = "version";

        /**
         * 是否收藏（0 未收藏，1 已收藏）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String FAVORITE = "favorite";
    }

    /**
     * data 表的列名定义接口
     * 存储笔记的具体内容（文本、通话记录等），支持多种 MIME 类型
     */
    public interface DataColumns {
        /**
         * 记录的唯一 ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * MIME 类型，用于区分数据子类型（如文本笔记、通话记录）
         * <P> 数据类型: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 所属笔记 ID，关联 note 表的 _id
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 创建时间（毫秒时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间（毫秒时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 内容文本（笔记的正文或通话记录的描述等）
         * <P> 数据类型: TEXT </P>
         */
        public static final String CONTENT = "content";

        /**
         * 通用整型数据字段 1，具体含义由 MIME 类型决定
         * <P> 数据类型: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * 通用整型数据字段 2
         * <P> 数据类型: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * 通用文本数据字段 3
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * 通用文本数据字段 4
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * 通用文本数据字段 5
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本笔记子类型，实现 DataColumns 接口
     * 用于存储普通文本笔记的内容和模式（普通/ checklist）
     */
    public static final class TextNote implements DataColumns {
        /**
         * 模式字段：1 表示 checklist 模式，0 表示普通文本模式
         * <P> 数据类型: Integer </P>
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;   // checklist 模式常量

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";      // 多行数据 MIME
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note"; // 单条数据 MIME

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录笔记子类型，实现 DataColumns 接口
     * 用于存储通话记录相关信息（电话号码、通话时间）
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话日期（毫秒时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码
         * <P> 数据类型: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";      // 多行数据 MIME
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note"; // 单条数据 MIME

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}