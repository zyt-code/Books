# AndroidBooks - EPUB Reader

一个基于 Jetpack Compose 的现代化 Android EPUB 阅读器应用，灵感来自 Apple Books。

## 项目特点

- **完全采用 Jetpack Compose** - 无 XML 布局，现代化声明式 UI
- **Material 3 设计** - 支持动态主题色
- **自研 EPUB 引擎** - 基于 WebView + JavaScript Bridge
- **Clean Architecture** - 清晰的分层架构（UI → ViewModel → Domain → Data）
- **现代技术栈** - Kotlin, Coroutines, Flow, Hilt, Room, DataStore

## 技术栈

- **语言**: Kotlin 2.0+
- **UI**: Jetpack Compose 1.7+ & Material 3
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **数据库**: Room
- **数据存储**: Proto DataStore
- **图片加载**: Coil
- **异步**: Kotlin Coroutines & Flow
- **导航**: Compose Navigation

## 项目结构

```
app/src/main/java/com/androidbooks/
├── data/
│   ├── epub/              # EPUB 解析器
│   ├── local/             # Room 数据库和 DataStore
│   └── repository/        # Repository 实现
├── domain/
│   ├── model/             # 领域模型
│   ├── repository/        # Repository 接口
│   └── usecase/           # 业务用例
├── presentation/
│   ├── bookshelf/         # 书架界面
│   ├── reader/            # 阅读器界面
│   ├── navigation/        # 导航配置
│   └── theme/             # Material 3 主题
└── di/                    # Hilt 依赖注入模块
```

## 核心功能

### 书架 (Bookshelf)
- 网格展示书籍封面
- 支持多种排序方式（最近阅读、标题、作者）
- 通过 SAF 导入 EPUB 文件
- 删除书籍

### 阅读器 (Reader)
- WebView 渲染 EPUB 内容
- 自定义字体大小、行高
- 三种主题模式（日间、夜间、护眼）
- 章节导航和目录
- 自动保存阅读进度
- 左右翻页

## 构建项目

### 前置要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK API 34

### 构建步骤

1. 克隆项目
```bash
git clone <your-repo-url>
cd AndroidBooks
```

2. 打开 Android Studio 并导入项目

3. 同步 Gradle
```bash
./gradlew build
```

4. 运行应用
```bash
./gradlew installDebug
```

或直接在 Android Studio 中点击 Run

## 开发说明

- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 34 (Android 14)
- **编译 SDK**: API 34

### EPUB 文件处理

1. EPUB 文件通过 SAF 导入后会被复制到应用私有目录
2. 解析器提取元数据、章节列表、封面和目录
3. 内容缓存到 `files/books/{bookId}/content/` 目录
4. WebView 加载缓存的 HTML 文件并注入自定义样式

### WebView 安全性

- 仅加载应用私有目录的内容
- 禁用跨域文件访问
- JavaScript 仅用于阅读进度跟踪

## 架构说明

项目采用 **单向数据流 (UDF)** 架构：

```
User Action → ViewModel → UseCase → Repository → Data Source
     ↑                                                  ↓
     └─────────────── UIState ←───────────────────────┘
```

- **UI Layer**: Compose screens 订阅 ViewModel 的 StateFlow
- **ViewModel Layer**: 处理用户事件，调用 UseCases，暴露 UIState
- **Domain Layer**: 纯 Kotlin 业务逻辑，无 Android 依赖
- **Data Layer**: Room, DataStore, File System 操作

## 待实现功能

- [ ] 书签功能
- [ ] 文本高亮和笔记
- [ ] 全文搜索
- [ ] PDF 支持
- [ ] 文本转语音 (TTS)
- [ ] 跨设备云同步

## 许可证

MIT License

## 致谢

本项目参考了 Apple Books 的设计理念和用户体验。
