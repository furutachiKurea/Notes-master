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

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x大小便签Widget的具体实现类。
 * 负责2x尺寸Widget的特定配置，包括布局、背景资源和类型标识。
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {
    /**
     * Widget更新时的回调方法。
     * @param context 应用上下文
     * @param appWidgetManager Widget管理器
     * @param appWidgetIds 需要更新的Widget ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回2x Widget的布局资源ID。
     * @return 2x Widget的布局资源ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 获取2x Widget对应背景色的资源ID。
     * @param bgId 背景色ID
     * @return 2x Widget的背景资源ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 返回2x Widget的类型常量。
     * @return TYPE_WIDGET_2X
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}
