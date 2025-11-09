Apple Books 风格 Android 电子书阅读器
技术说明文档（方案 A：自研 EPUB 渲染器 + Jetpack Compose）
版本：1.0
最后更新：2025 年 11 月
目标平台：Android 8.0+（API 26），主推 Android 10+（API 29）及以上
核心理念：Compose First · Kotlin Native · 现代架构 · 用户体验优先

一、整体架构概览

本项目采用 分层单向数据流（Unidirectional Data Flow, UDF）架构，结合 Jetpack Compose 构建 UI，通过 自研 EPUB 解析与渲染引擎 实现类 Apple Books 的阅读体验。

┌──────────────────────┐
│ UI Layer │ ← Jetpack Compose (Material 3)
└──────────┬───────────┘
↓
┌──────────────────────┐
│ ViewModel Layer │ ← StateFlow<UIState>, UseCase 调用
└──────────┬───────────┘
↓
┌──────────────────────┐
│ Domain Layer │ ← 纯 Kotlin 业务逻辑 (UseCases)
└──────────┬───────────┘
↓
┌──────────────────────┐
│ Data Layer │ ← Repository + Room + File System
└──────────────────────┘

所有状态变更由用户交互或系统事件触发，经 ViewModel 处理后以不可变 UIState 形式暴露给 Compose，确保可预测性和可测试性。

二、核心技术栈（2025 最佳实践）

类别 技术选型
------------------ --------------------------------------------------------------------------
语言 Kotlin 2.0+
UI 框架 Jetpack Compose 1.7+（完全取代 View 系统）
主题设计 Material 3 + Dynamic Color（从书籍封面提取主题色）
状态管理 StateFlow<UIState> + collectAsStateWithLifecycle()
异步处理 Kotlin Coroutines + Flow
依赖注入 Hilt 2.50+
数据持久化 Room 2.6+（书籍元数据、书签、高亮）<br>DataStore Proto（用户偏好设置）
图片加载 Coil 2.6+（支持 Compose、内存优化、自动回收）
文件访问 Storage Access Framework (SAF) + DocumentFile（适配 Scoped Storage）
导航 Compose Navigation（类型安全、无 Fragment）
EPUB 解析 自研解析器（基于 Kotlin 标准库 + XML Pull Parser）
EPUB 渲染 定制 WebView + JavaScript Bridge + 动态 CSS 注入
多设备适配 calculateWindowSizeClass() + DevicePosture（手机/平板/折叠屏）
性能优化 Baseline Profiles + R8 Full Mode + Preload Reader Content

三、核心功能模块说明
1. 书架（Bookshelf）
功能点：
网格展示本地 EPUB 书籍封面（3:4 比例）
支持按书名、作者、最近阅读排序
“+” 按钮导入新书（调用 SAF）
长按弹出操作菜单（删除、重命名、查看详情）
空状态提示 & 加载骨架屏
技术实现：
使用 LazyVerticalGrid(columns = GridCells.Adaptive(150.dp))
封面图通过 Coil 从私有目录加载（路径：/files/books/{bookId}/cover.jpg）
书籍元数据存储于 Room 表 BookEntity
导入逻辑封装在 ImportEpubUseCase，复制文件至 App 私有目录避免权限问题

kotlin
@Entity(tableName = "books")
data class BookEntity(
@PrimaryKey val id: String,
val title: String,
val author: String,
val filePath: String,
val coverPath: String?,
val lastReadAt: Long,
val progressSpineIndex: Int = 0,
val progressOffset: Float = 0f
)

2. 阅读器（Reader）
功能点：
全屏沉浸式阅读（隐藏状态栏/导航栏）
左右滑动 or 点击边缘翻页
自定义字体、字号、行距、背景色（日间/夜间/护眼）
书签、文本高亮、笔记
自动保存阅读进度
目录跳转（TOC）
技术实现（方案 A 核心）：
2.1 EPUB 解析
使用 ZipInputStream 解压 EPUB（本质是 ZIP）
解析 META-INF/container.xml → 获取 content.opf
从 content.opf 提取：
<manifest> → 所有 HTML 资源路径（spine）
<metadata> → 书名、作者、语言
<guide> / <nav> → 目录结构（NCX 或 XHTML Nav）
封面图优先从 <meta name="cover"> 或 <item properties="cover-image"> 提取
2.2 渲染引擎
使用 AndroidView(factory = { WebView(context) }) 嵌入 WebView
启用必要设置：
kotlin
webView.settings.apply {
javaScriptEnabled = true
domStorageEnabled = true
builtInZoomControls = false
displayZoomControls = false
loadWithOverviewMode = true
useWideViewPort = true
}
关键：注入自定义 CSS
kotlin
private fun injectStyles(webView: WebView) {
val css = """
body {
font-family: '${userPrefs.fontFamily}';
font-size: ${userPrefs.fontSize}rem;
line-height: ${userPrefs.lineHeight};
background-color: ${theme.bgColor};
color: ${theme.textColor};
margin: 0 auto;
max-width: 800px;
padding: 20px;
}
""".trimIndent()
webView.evaluateJavascript("var style = document.createElement('style'); style.innerHTML = $css; document.head.appendChild(style);", null)
}

2.3 Kotlin ↔ JavaScript 双向通信
Kotlin → JS：webView.evaluateJavascript(jsCode, callback)
JS → Kotlin：通过 @JavascriptInterface 注册桥接对象

kotlin
class WebAppInterface(private val onProgressUpdate: (Float) -> Unit) {
@JavascriptInterface
fun onScroll(percent: Float) {
// 主线程回调
Handler(Looper.getMainLooper()).post { onProgressUpdate(percent) }
}

@JavascriptInterface
fun onSelection(text: String, start: Int, end: Int) {
// 触发高亮菜单
}
}
webView.addJavascriptInterface(WebAppInterface { offset ->
viewModel.updateReadingProgress(offset)
}, "Android")
2.4 翻页逻辑
滑动翻页：使用 Modifier.pointerInput 捕获手势
左滑 → 下一章（若当前章未结束，则滚动到底部）
右滑 → 上一章
点击翻页：屏幕左右 20% 区域点击触发

3. 用户偏好与同步（可选）
使用 Proto DataStore 存储：
protobuf
message UserPreferences {
string font_family = 1;
float font_size = 2;
float line_height = 3;
ThemeMode theme_mode = 4;
enum ThemeMode { DAY = 0; NIGHT = 1; SEPIA = 2; }
}
云同步（未来扩展）：通过 Firebase Firestore 同步 ReadingProgress 和 Highlight

四、关键业务逻辑流程
1. 导入 EPUB 流程
mermaid
sequenceDiagram
participant User
participant BookshelfScreen
participant ImportEpubUseCase
participant EpubParser
participant BookRepository

User->>BookshelfScreen: 点击“+”
BookshelfScreen->>ImportEpubUseCase: launch SAF
ImportEpubUseCase->>ImportEpubUseCase: 复制文件到 /files/books/{uuid}/book.epub
ImportEpubUseCase->>EpubParser: parseMetadata(file)
EpubParser-->>ImportEpubUseCase: BookMetadata
ImportEpubUseCase->>BookRepository: insert(BookEntity)
BookRepository-->>BookshelfScreen: 更新 UIState
2. 打开书籍并渲染
mermaid
flowchart TD
A[用户点击书籍] --> B{是否首次打开?}
B -- 是 --> C[解压 EPUB 到缓存目录]
B -- 否 --> D[直接读取缓存]
C --> E[解析 spine 列表和 TOC]
D --> E
E --> F[启动 ReaderScreen]
F --> G[WebView 加载 spine[progressSpineIndex]]
G --> H[注入用户 CSS]
H --> I[注册 JS Bridge]
I --> J[监听滚动/选择事件]
3. 保存阅读进度
每 2 秒或页面切换时，通过 JS 获取当前滚动百分比
结合当前 spine index，写入 Room
下次打开自动定位到该位置

五、安全与兼容性考虑
WebView 安全：
禁用 file:// 访问（仅加载 content:// 或私有目录）
不启用 setAllowFileAccessFromFileURLs
过滤 EPUB 中的 <script> 标签（可选）
Android 10+ 文件访问：
所有书籍文件存储于 context.getExternalFilesDir(null)，无需运行时权限
EPUB 兼容性：
支持 EPUB 2/3 标准
对非标准 EPUB 进行容错处理（如缺失 cover）

六、未来扩展方向
✅ PDF 支持：通过 Dynamic Feature Module 按需下载
✅ 文本转语音（TTS）：集成 Android TTS API
✅ 跨平台共享：使用 Kotlin Multiplatform 共享 EPUB 解析逻辑（iOS/macOS）
✅ AI 笔记摘要：本地 LLM 生成章节摘要（需设备支持）

七、附录：依赖清单（build.gradle.kts 片段）

kotlin
dependencies {
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.compose.ui:ui:1.7.0")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
implementation("io.coil-kt:coil-compose:2.6.0")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
}

📌 备注：本方案完全基于 Android 官方现代开发范式，避免使用已废弃 API，确保应用在未来 3-5 年内保持技术先进性与可维护性。

