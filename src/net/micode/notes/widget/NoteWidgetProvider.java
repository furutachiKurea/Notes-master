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

package net.micode.notes.widget;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 便签Widget的基类抽象提供者。
 * 负责Widget的创建、更新、删除等生命周期管理，以及Widget的显示和交互逻辑。
 * 子类需要实现具体的布局、背景资源和Widget类型选择。
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    // 从数据库查询便签时所需的列
    public static final String [] PROJECTION = new String [] {
        NoteColumns.ID,                  // 便签ID
        NoteColumns.BG_COLOR_ID,        // 背景颜色ID
        NoteColumns.SNIPPET              // 便签摘要（预览内容）
    };

    // 数据库查询结果中各列的索引位置
    public static final int COLUMN_ID           = 0;   // ID列位置
    public static final int COLUMN_BG_COLOR_ID  = 1;   // 背景色ID列位置
    public static final int COLUMN_SNIPPET      = 2;   // 摘要列位置

    private static final String TAG = "NoteWidgetProvider";

    /**
     * Widget被删除时的回调方法。
     * 清除便签与Widget的关联关系，将便签的WIDGET_ID设置为无效值。
     * @param context 应用上下文
     * @param appWidgetIds 被删除的Widget的ID数组
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 更新便签表，将与该Widget关联的便签的WIDGET_ID置为无效
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 根据Widget ID查询关联的便签信息。
     * 只查询非垃圾箱中的便签，获取便签的基本信息用于Widget显示。
     * @param context 应用上下文
     * @param widgetId Widget的ID
     * @return 包含便签ID、背景色、摘要的游标
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 公开的更新接口，调用内部实现方法，默认不使用隐私模式。
     * @param context 应用上下文
     * @param appWidgetManager Widget管理器
     * @param appWidgetIds 需要更新的Widget ID数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 内部更新实现方法，根据隐私模式标志更新Widget的显示内容。
     * @param context 应用上下文
     * @param appWidgetManager Widget管理器
     * @param appWidgetIds 需要更新的Widget ID数组
     * @param privacyMode 是否启用隐私模式（隐私模式下不显示便签内容）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
            boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 初始化Widget的背景色和内容
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // 查询Widget关联的便签信息
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 获取便签的摘要、背景色和ID
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    // 如果没有关联的便签，显示提示信息
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                // 创建RemoteViews用于更新Widget UI
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
                
                /**
                 * 生成点击Widget时的待定意图，用于启动相应的Activity
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式下显示提示信息而不显示实际内容
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式下显示便签摘要
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // 绑定点击事件和更新Widget
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    /**
     * 获取指定背景色ID对应的资源ID。
     * 由子类实现，以支持不同尺寸Widget的不同背景资源。
     * @param bgId 背景色ID
     * @return 背景资源ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取Widget的布局资源ID。
     * 由子类实现，以支持不同尺寸的Widget布局。
     * @return 布局资源ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取Widget的类型。
     * 由子类实现，标识Widget的尺寸类型（2x或4x）。
     * @return Widget类型常量
     */
    protected abstract int getWidgetType();
}
