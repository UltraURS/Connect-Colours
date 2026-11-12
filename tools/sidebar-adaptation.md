# 0.2.3 返回手势呼出智能侧边栏：适配进度

## 构建号 14：最大位移计时与桌面原生主屏

新增用户提供的桌面 APK，包名 com.android.launcher，版本 13.0.44，SHA-256 70bdf722ba7641e0c626dce7cda3ce3f36ac957aea339ccf246aa19627bbaac0。JADX 完整反编译报告 2 个错误，所用 Launcher.onNewIntent、Workspace.moveToDefaultScreen 和 DEFAULT_PAGE 等声明已通过原始 DEX 核验。

该版本 Launcher 为 exported singleTask。前台正常桌面收到 MAIN/HOME 后，原生 onNewIntent 排队调用 moveToDefaultScreen；由 Workspace.DEFAULT_PAGE 决定目标，普通模式为 0，支持简易模式速拨页时为 1。模块仅在确切 Launcher 主页面保持焦点、手势有效且未 READY/取消时发送显式 HOME Intent，限制已核验的 13.0.44，无需新增桌面 Hook 作用域。原生仍负责模态状态、触摸及切页判断，实际回主屏待用户验证。

最大位移按 SideGestureNavView.updateTargetPoint 的原生位移缩放和左右整数取整计算：getAnimSwitchDistance()/0.32299998f 约 120dp，而非 getBackDistance 的 100dp。首次移动仅进入 TRACKING，不计时；首次进入最大范围开始 500ms，退出最大范围清零。计时任务按剩余时间安排；MOVE/UP 使用事件时间，定时器使用 uptime。READY 后回划整体取消。回主屏与侧边栏结果互斥。

同步设置文案、更新文档及阈值/取消/主屏互斥测试。以下保留旧构建历史，旧版首次移动计时说明不适用于构建号 14。

## 构建号 13：桌面早期拦截

用户确认构建号 12 仅剩桌面无法触发。14:38/14:40 日志中应用内 side=0/1 均 accepted=true 且 native open requested；桌面连续 checkAllowGesture=false，但 flags=131072、quickstepDisabled=false、backAllowed=true、blockingActivity=false，且未到 disableGestureInLauncher。结合原生代码，仍可能是触摸区域排除或预处理中的 disableBack/disableAll 等早期条件，日志不能单独断定具体分支。

仅在 Launcher 主页面且焦点包也是 Launcher 时增加例外：edgeContains 判断的对象必须是本 detector 的 mExcludeRegion，才将命中视为未命中；保留 PiP、导航浮层、侧边栏场景和手写笔区域检查。disableBack 例外通过 ThreadLocal 限定在该桌面 checkAllowGesture 调用期间，调用结束恢复，保留 disableAll 和其余原生限制。桌面短滑继续按取消处理，避免实际发送返回。新增各分支命中日志和被拒绝时的顶层组件日志。

新增桌面焦点、覆盖窗口、手势教学、其他应用及未知前台的边界测试。需真机验证桌面左右触发、短滑不返回、回划取消以及应用内原有触发。

## 构建号 12：依据 10 月 26 日日志修正

两份日志确认 SystemUI UID 为 10169，ContentResolver.call 在获取 provider 时被 com.coloros.sidebar.permission.data 拒绝。原先认为 SystemUI 是 UID 1000 不成立。

增加 system_server 的固定动作中转：AMS.systemReady 后注册接收器，发送者必须持有 android.permission.STATUS_BAR_SERVICE（已核验 SystemUI APK 声明）。仅接受 side=0/1，不接收外部 URI 或方法名；工作线程以系统身份调用原 provider。保留 provider 原始权限和 UID 1000 校验。SystemUI 通过定向有序广播获取入队结果；未注册、未装 Hook 或调用失败显示提示。当前按主用户环境适配。

桌面诊断中的 mTopActivity 一直为 null，改用原生 Util.getTopActivityComponentName 获取前台组件，配合焦点包判断确切 Launcher 页面；页面变更检查也使用该来源。保留其他原生手势禁用条件，不宣称已排除所有桌面拦截。诊断额度改为前 40 次边缘检查，避免被解锁时普通触摸耗尽。

需系统框架、SystemUI、智能侧边栏作用域并重启。两侧展开、桌面短滑和回划取消仍需真机复验。

本地验证：59 项测试通过（新增 4 项中转测试），Lint 无错误，新增 AMS/Util 声明通过原始 DEX 校验。

## 构建号 11：用户反馈与待定位问题

用户反馈构建号 10 在桌面无法触发，应用内松手提示“暂时无法呼出智能侧边栏”。该提示对应 SystemUI 的 ContentResolver.call 异常捕获，不能据此判断是 provider 不存在、权限、运行时 Hook 异常还是其他原因。此前已找到的桌面排除位置也不足以解释设备上实际拦截条件。

按用户最新要求将触发时间改为 500 毫秒，更新 499/500 毫秒边界测试和用户文档。增加每次进程启动前 16 次触摸的顶层页面、桌面判定、原生允许状态与限制标志诊断，以及侧边栏请求异常堆栈。尚未宣称修复桌面或侧边栏调用，需复现后的模块日志及详细日志。下方记录保留构建号 10 的实现背景。

## 已完成的独立逻辑

新增 `SidebarGesture` 和对应回归测试：首次向内移动开始计时，800 毫秒且返回位置有效时进入 READY；单次提示；回划后整次取消；新触摸前不再激活；松手仅允许一次呼出；两侧方向分别保存；中断和快速松手不呼出。

该类已接入 SystemUI Hook，版本提升为 0.2.3（versionCode 10），新增默认开启的设置开关。APK 采用新的 0.2.3 文件名，保留已发布的 0.2.2 APK。真实设备上的手势、震动、展开方向仍待验证。

## 现有 SystemUI 13.01.21 中确认的入口

- `com.oplusos.systemui.navigationbar.gesture.sidegesture.SideGestureDetector.onMotionEventImpl(MotionEvent)`：原生边缘事件、输入接管、返回与切换上一应用的分派。
- `detectGestureState(int,int,int)`：状态 1 为取消区域，2 为返回，3 为切换上一应用。接入时需明确处理原有长滑切换应用与新功能的冲突。
- `SideGestureViewManager.mNavLeftView/mNavRightView` 与 `SideGestureNavView.setBackIcon(Bitmap)`：左右侧返回图标，可作为待确认状态的替换位置，结束时必须恢复。
- `SideGesturePreProcess.disableGestureInLauncher()`：依据焦点窗口与 `NavBarSettingsValueProxy.getLauncherSlidSlipEnable` 禁用桌面手势。不能直接跳过整个 `checkSideGesture`，否则会遗漏其他原生限制。桌面短滑仍需避免注入返回事件。
- `EdgePanelHelper` 通过资源指定的 action/package 绑定智能侧边栏，其 `IEdgePanelCallback` 仅有 `onMinimize(boolean)` 与 `onDockedStackExistsChanged(boolean)`，未提供左右指定展开参数。不能据此假设可完成需求。

## 已补充的智能侧边栏与实现

用户提供的 `com.coloros.common.App.apk.1` 实际包名为 `com.coloros.smartsidebar`，版本 15.2.7（15002007），SHA-256 为 `955946d8abdf754130b50dd03dd8740fe2b16e31531eac4df67e114bcdbf0e44`。副本 `.tooling/apks/sidebar.apk`，反编译 `.tooling/sidebar`；jadx 报告 17 个错误，已针对采用的声明做原始 DEX 核验。

- `SideBarProvider` 现有 `OVOICE_OPEN_FLOATBAR_ACTION` 调用 `EdgePanelController.openSmartSideBarAction()`，最终调用 `DisplayDataManager.f()`，保留 `checkPreScrollTasks()` 的原生授权/更新前置处理。
- 新增专用 provider call `connect_colours_open_sidebar`，只接受 UID 1000 的调用，且保留原 provider 权限。参数 side=0/1；SystemUI 在工作线程请求，侧边栏在主线程处理，返回 accepted 仅表示入队，不表示窗口已显示。
- 原始 DEX 单例字段为 `DisplayDataManager.a`，不能使用 jadx 展示的 `f11720a`。`r(boolean,boolean)` 更新左右方向及 UI 进程位置，随后排队调用 `f()`；通过同一主线程队列保证浮条更新在展开之前。原生浮条也随之移到触发侧，未直接写入用户设置表。
- 侧边栏未开启、锁屏、窗口未就绪或原生全局场景禁止时不展开。未开启/未就绪显示短提示，错误与原生请求记录模块日志。
- SystemUI 复用原生输入接管和返回动画；对需消费的松手或回划，传入副本 ACTION_CANCEL 让原生代码清理状态，避免注入返回键。未达阈值的应用内短滑仍走原生返回。
- 首次向内移动时安排 800ms 计时；继续移动不重置计时。回划达到系统 touch slop 后取消；多指、系统取消、手势不再有效、方向变化或页面变化不允许确认呼出。
- 在 READY 状态的 onDraw 内暂时替换返回箭头位图，绘制结束立即恢复原位图。使用代码绘制侧边栏轮廓图标，不修改原生资源。进入 READY 时请求一次系统触感反馈。
- 桌面仅放开确切 Launcher 页面对应的排除，保留其他原生允许条件；桌面短滑转换成取消。启用本功能时阻止原有 500ms 切换上一应用计时，与 800ms 新手势避免冲突。
- 新增 LSPosed 作用域 `com.coloros.smartsidebar`。SystemUI 和侧边栏按提供的版本适配；配置沿用共享偏好，修改后重启生效。

## 验证边界

本地覆盖状态机、升级默认开关、设置保存及既有功能测试，核验所用 DEX 声明，并执行 Lint、签名和 ZIP 对齐检查。未连接真机，不能据此宣称跨进程展开和桌面系统手势已实测。真机需依照需求文档逐项检查左右侧、首次/重复呼出、回划取消、桌面短滑、原生侧边栏开关和模块开关。
