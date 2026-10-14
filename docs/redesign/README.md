# 界面改版对照

这个目录是 2026-10 那次全应用界面改版的对照记录。

- `before/` —— 改版前的界面（提交 `ed1c9d4` 构建的 APK）
- `after/` —— 改版后的界面

两份用的是**同一份课表、同一台设备、同一个尺寸**，可以直接对着看差异。
`docs/screenshots/` 里 README 顶部那组图是 `after/` 里浅色那几张的副本。

## 截图里的数据

全部是**虚构的演示课表**（高等数学A（一）、计算机网络之类），
与本项目作者或任何同学的真实课表无关，只为把界面填满。

## 拍摄环境

| 项目 | 值 |
| --- | --- |
| 设备 | Pixel 10 Pro 模拟器 |
| 分辨率 | 1280×2856 @ 480dpi（≈426×952dp） |
| 系统 | Android 16（API 37） |
| 例外 | `08-shortphone-dark.png` 用的是 1080×2000（≈360×667dp），用来验证矮屏下的格子自适应 |

## 文件对照

| before/ | after/ | 说明 |
| --- | --- | --- |
| `01-week.png` | `01-week.png` | 周课表（浅色） |
| `02-today.png` | `02-today.png` | 今日课程 |
| `03-course-detail.png` | `03-course-detail.png` | 课程详情浮层 |
| `04-settings.png` | `04-settings.png` | 设置页 |
| `05-about.png` | `05-about.png` | 关于页 |
| — | `06-easter-egg.png` | 彩蛋弹窗 |
| — | `07-week-dark.png` | 周课表（深色） |
| — | `08-shortphone-dark.png` | 矮屏下的格子降级 |
| — | `09-first-run.png` | 首次启动说明 |
