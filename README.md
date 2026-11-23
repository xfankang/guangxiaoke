# 广小课

广州理工学院（GZIST）学生用的原生 Android 课表 App。
登录学校统一身份认证后自动导入整学期课表，1~2 秒完成。

<p align="center">
  <img src="docs/screenshots/01-课表页.png" width="24%" />
  <img src="docs/screenshots/02-今日课程.png" width="24%" />
  <img src="docs/screenshots/03-课程详情.png" width="24%" />
  <img src="docs/screenshots/06-彩蛋.png" width="24%" />
</p>

## 功能

- **课表导入**：登录统一身份认证后直连接口拉取整学期课表（1~2 秒）；接口数据须通过双向校验，不通过则自动回退逐周页面解析
- **周课表 / 今日课程**：左右滑动或按钮切周，带弹性过渡动画
- **今日进度**：按当前时间标注每门课是否上完（nb上完一节课 / nb又上完一节课 / 未完成），全部上完会提示「已完成今日所有课程」
- **课程详情**：点课程在手指附近弹出浮层（课程名 / 地点 / 教师 / 时间），再点同一位置收起
- **下一节倒计时**：今日课程页显示「下一节 10:20 · 高等数学 · 还有 12 分钟」/「正在上课 · 还有 25 分钟」
- **桌面小组件**：一个组件列出当天全部课程（按尺寸自适应行数，放不下显示「还有 N 门课」），头部倒计时实时走秒（Chronometer，App 零唤醒），点击打开 App
- **分享课表为图片**：设置 → 课表 → 分享课表为图片，生成本周列表式长图（完整课名 / 时间 / 教室 / 教师）
- **学期周次校正**：自动反推的开学日期不对时，在设置里选「现在是第几周」校正（折算成日期保存）
- **周末折叠**：周课表可只显示周一至周五（今日课程不受影响）
- **开发者模式**（设置 → 开发者选项，默认关）：开启后才显示导入诊断按钮、同课多时段、课表数据自检等排查工具；普通用户界面保持清爽
- **检查更新**：每天最多一次查询 GitHub 版本号，发现新版只跳浏览器下载（不新增任何权限）
- **悬浮 Dock 栏**：胶囊式 Dock，点按带滑动指示器动画，可在设置里切回贴边状态栏
- **外观**：深色模式、自定义背景（可拖动、缩放调整构图）、恢复默认
- **彩蛋**：设置页「最初的起点」

## 环境要求

| 项目 | 版本 |
| --- | --- |
| minSdk | 30（Android 11） |
| targetSdk / compileSdk | 37 |
| Kotlin | 2.4.21 |
| AGP | 9.4.1（内置 Kotlin，无需再应用 kotlin-android 插件） |
| Gradle | 9.8.1 |
| UI 组件库 | Miuix by YuKongA |

## 构建

```bash
# 调试版
./gradlew :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# 正式版（需要签名配置，见下）
./gradlew :app:assembleRelease
```

正式版签名读取根目录的 `keystore.properties`（该文件与 keystore 均**未提交**）：

```properties
storeFile=keystore/guangxiaoke.jks
storePassword=你的密码
keyAlias=guangxiaoke
keyPassword=你的密码
```

没有这个文件时 release 会退化成未签名包，不影响 debug 构建。

## 项目结构

```
app/src/main/java/com/jianke/schedule/
├── data/
│   ├── ZfsoftParser.kt      正方教务接口（kbList）解析
│   ├── ApiValidator.kt      接口数据与页面数据的双向校验
│   ├── MobileDomParser.kt   手机版课表页 DOM 解析
│   ├── WeekParser.kt        「当前第几周」统一识别（优先认表头）
│   ├── ScheduleStore.kt     课表本地存储
│   ├── ImportDiagnostics.kt 导入诊断文本（周次核对表 / 接口报告）
│   ├── UpdateChecker.kt     检查更新（版本比较 / Release 解析 / 受限网络请求）
│   ├── NextClass.kt         今日课程状态 / 小组件全天数据 / 下一次刷新时刻
│   ├── CourseSessions.kt    同一门课的多个时段聚合 / 周次文本格式化
│   ├── WidgetLayout.kt      小组件尺寸 → 行数/信息量
│   ├── ScheduleSelfCheck.kt 课表数据自检报告
│   ├── CourseColors.kt      课程配色
│   ├── ClassTimes.kt        作息时间
│   ├── ImageDecode.kt       图片降采样解码（壁纸 / 彩蛋共用）
│   ├── Prefs.kt             设置存储
│   ├── Wallpaper.kt         自定义背景
│   └── EasterEgg.kt         彩蛋图片加载
├── widget/
│   └── TodayWidgetProvider.kt 桌面小组件（RemoteViews + Chronometer 实时倒计时）
├── web/
│   ├── SchoolEndpoints.kt   学校各系统地址
│   ├── ScheduleJs.kt        页面解析与翻周注入脚本
│   └── ApiProbeJs.kt        接口探测脚本
└── ui/
    ├── AppRoot.kt           主题 / 壁纸 / 底栏 / 导航
    ├── HomeScreen.kt        周课表、今日课程、课程浮层
    ├── ImportScreen.kt      导入流程（接口优先，页面兜底）
    ├── SettingsScreen.kt    设置
    ├── FloatingDock.kt      悬浮 Dock 栏
    ├── ShareCard.kt         课表分享长图（android.graphics 手绘）
    ├── WallpaperEditor.kt   背景构图调整
    ├── SettingsDialogs.kt   学期周次校正弹窗
    ├── UpdateAvailableDialog.kt 发现新版本弹窗
    ├── AboutScreen.kt       关于与鸣谢
    ├── EasterEggDialog.kt   彩蛋弹窗
    └── PrivacyDialog.kt     首次启动提示
```

## 数据与隐私

- 课表、设置、壁纸**只保存在本机** App 私有目录，不联网上传
- 导入时通过 WebView 登录学校系统，账号密码不经过本 App，也不做任何存储
- 启动时（每天最多一次）会向 GitHub 查询是否有新版本：只发一个 GET 请求读版本号，
  不携带任何课表、账号或设备信息，可在设置里关闭「自动检查更新」
- 桌面小组件只读取本机课表；「分享课表为图片」把 PNG 写进 App 私有缓存目录，
  经 FileProvider 交给系统分享面板，App 本身不联网上传

## 鸣谢

| 名字 | 贡献 |
| --- | --- |
| DeepSeek Harness | 开发与调试环境 |
| YuKongA | Miuix 界面组件库 |
| 薪音 | 测试 |
| curian | 测试 |
| xxl | 测试 |
| xfankang | 作者 |

## 许可与致谢补充

- Dock 栏液态玻璃效果移植自 [KernelSU 管理器](https://github.com/tiann/KernelSU)
  （GPL-3.0，`ui/component/liquid` 与 `FloatingBottomBar`，文件头已注明），
  其折射着色器源自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
  （Apache 2.0）。因此本项目整体以 GPL-3.0 发布。

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。
