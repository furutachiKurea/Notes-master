# MiCode Notes 注释分工

## 项目简介

MiCode 便签（小米便签社区开源版），Android 原生应用，使用 SQLite + ContentProvider 存储数据，支持 Google Tasks 同步、桌面小部件、闹钟提醒等功能。

---

## 分工表

### 成员 A — 数据层 + 工具层

| 文件 | 说明 |
|---|---|
| `src/.../data/Notes.java` | 全局常量、数据库表名、ContentProvider URI 定义 |
| `src/.../data/NotesDatabaseHelper.java` | SQLite 数据库创建、升级、触发器 |
| `src/.../data/NotesProvider.java` | ContentProvider 增删改查接口 |
| `src/.../data/Contact.java` | 联系人姓名查询（带内存缓存） |
| `src/.../tool/DataUtils.java` | 批量删除、移动笔记等数据操作工具 |
| `src/.../tool/BackupUtils.java` | 便签备份导出到外部存储 |
| `src/.../tool/ResourceParser.java` | 便签颜色和字体大小资源映射 |
| `src/.../tool/GTaskStringUtils.java` | GTask 同步相关 JSON 字符串常量 |

---

### 成员 B — GTask 数据模型层

| 文件 | 说明 |
|---|---|
| `src/.../gtask/data/Node.java` | 同步节点抽象基类，定义同步操作枚举 |
| `src/.../gtask/data/Task.java` | 单条 GTask 任务，含 JSON 序列化逻辑 |
| `src/.../gtask/data/TaskList.java` | GTask 任务列表，管理子 Task 集合 |
| `src/.../gtask/data/MetaData.java` | GTask ID 与本地 Note ID 的映射元数据 |
| `src/.../gtask/data/SqlNote.java` | 本地数据库 Note 的封装，与 GTask 对象互转 |
| `src/.../gtask/data/SqlData.java` | 本地数据库 Data 行封装 |
| `src/.../gtask/exception/ActionFailureException.java` | 同步动作失败自定义异常 |
| `src/.../gtask/exception/NetworkFailureException.java` | 网络失败自定义异常 |

---

### 成员 C — GTask 同步服务层 + 业务模型层

| 文件 | 说明 |
|---|---|
| `src/.../gtask/remote/GTaskClient.java` | GTask HTTP 客户端，登录认证、Token 获取、任务 CRUD |
| `src/.../gtask/remote/GTaskManager.java` | 同步核心管理器，处理本地与远端数据的 diff 和 merge |
| `src/.../gtask/remote/GTaskASyncTask.java` | AsyncTask 包装，后台执行同步并显示通知进度 |
| `src/.../gtask/remote/GTaskSyncService.java` | Android Service，管理同步任务生命周期 |
| `src/.../model/Note.java` | Note 数据模型，封装通过 ContentProvider 的增删改操作 |
| `src/.../model/WorkingNote.java` | 当前编辑便签的内存模型，管理颜色、提醒、内容等状态 |

---

### 成员 D — UI 层 + Widget 层

| 文件 | 说明 |
|---|---|
| `src/.../ui/NotesListActivity.java` | 主列表界面，支持新建、删除、移动、搜索、同步入口 |
| `src/.../ui/NoteEditActivity.java` | 便签编辑界面，支持清单模式、颜色设置、定时提醒 |
| `src/.../ui/NoteEditText.java` | 自定义 EditText，处理回车分割列表项等编辑行为 |
| `src/.../ui/NotesListAdapter.java` | 便签列表 CursorAdapter |
| `src/.../ui/NotesListItem.java` | 自定义列表项 View，渲染标题、时间、背景色 |
| `src/.../ui/NoteItemData.java` | 列表项数据封装，从 Cursor 读取便签各字段 |
| `src/.../ui/FoldersListAdapter.java` | 文件夹列表适配器（移动便签时的选择对话框） |
| `src/.../ui/DropdownMenu.java` | 下拉菜单封装（基于 PopupMenu） |
| `src/.../ui/DateTimePicker.java` | 自定义日期时间滚轮选择控件 |
| `src/.../ui/DateTimePickerDialog.java` | 包装 DateTimePicker 的提醒时间设置对话框 |
| `src/.../ui/AlarmAlertActivity.java` | 闹钟到点时展示便签内容的提醒弹窗 |
| `src/.../ui/AlarmInitReceiver.java` | 开机广播接收器，重启后重新注册所有闹钟 |
| `src/.../ui/AlarmReceiver.java` | 闹钟触发广播接收器，启动提醒弹窗 |
| `src/.../ui/NotesPreferenceActivity.java` | 设置页，管理 Google 账号绑定和同步配置 |
| `src/.../widget/NoteWidgetProvider.java` | 桌面小部件基类，处理数据加载和点击跳转 |
| `src/.../widget/NoteWidgetProvider_2x.java` | 2×2 尺寸 Widget |
| `src/.../widget/NoteWidgetProvider_4x.java` | 4×4 尺寸 Widget |
