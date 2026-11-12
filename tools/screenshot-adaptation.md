# 三键截图适配记录（versionCode 6）

## 已确认需求

- 在截屏设置的“电源键+音量减键”下方加入“电源键+音量减键+音量加键”。
- 新选项默认关闭，三键同时短按调用原生普通全屏截图流程。
- 双键、三键开关互斥：开启一个自动关闭另一个，允许同时关闭。

## 已分析文件

- 截屏 APK：14.8.24，包名 `com.oplus.screenshot`，SHA-256 `58035b696bfb1adf870889186461df39973be0250412593c2aaa6dcbcf1ca4db`。
- services.jar：SHA-256 `9354c963026f40bd7473e2afa4e0b0f16d0037b615f59d3480274f78c750e1a0`。
- 原始副本位于 `.tooling/apks`；反编译输出 `.tooling/screenshot`、`.tooling/services`。jadx 分别报告 3、59 个方法错误，不可直接把反编译结果当成完整控制流。

## 设置页

- `com.oplus.screenshot.setting.ScreenshotSettingFragment` 加载 `res/xml/screenshot_setting.xml`。
- 分类 `pref_key_setting_gesture_container`，双键开关 `pref_key_physical_keys`，紧随其后的跳转项 `pref_key_Learn_key`。
- 双键写入路径：`onPreferenceChange` → `onPrefPhysicalKeyChange` → `dataManager.A(boolean)`。
- `v5.g` 中对应设置键 `oplus_customize_screenshot_enable_physical_keys`，经 `v5.t` 访问（仍需核对具体用户及设置表）。
- 此 APK 的 AndroidX Preference 方法名经过混淆，不能直接复制无线设置 Hook 中的反射方法名。示例：`Preference.v()` 为 key，`p()` 为 Context，监听器为 `Preference$b`。

## 系统按键

- `PhoneWindowManager.initKeyCombinationRules()` 建立音量减+电源截图、音量减+音量加无障碍、音量加+电源静音/电源菜单等规则。
- 截图路径：`interceptScreenshotChord(int,int,long)` → Handler 消息 16 → `handleScreenShot(int,int)` → `mDefaultDisplayPolicy.takeScreenshot(type,source)`。
- `PhoneWindowManager` 还调用 `IPhoneWindowManagerExt.getInputExtension()` 返回的 `IInputExt`，涉及电源按下/松开、长按及铃声组合等。
- 提供的 services.jar 包含上述厂商接口，未包含实际厂商扩展实现。`interceptKeyBeforeQueueing` 和 `interceptKeyBeforeDispatching` 也未能正常反编译。需补充手机当前固件的 `oplus-services.jar` 等承载扩展实现的框架，核查三键处理与现有组合键的先后顺序、取消和松键清理。

## 厂商框架与实现

- 用户补充 `framework/oplus-services.jar`，SHA-256 `27f186a291911f459f08179924bae2b6d510650702377e0375c30e4561b95bf8`。包含 `SubPhoneWindowManager`、`PhoneWindowManagerExtImpl` 和 `InputPolicy`。
- Hook `SubPhoneWindowManager.init` 后注册配置观察者，并在其 `interceptKeyBeforeQueueing` 跟踪内置设备的音量加/减及电源键；保留原方法以完成原生松键和 wakelock 清理。`interceptKeyBeforeDispatching` 在识别窗口内延后分发，成功后消费该轮按键。
- 三键窗口 150 毫秒，与框架原 `KeyCombinationManager` 的窗口一致。只处理亮屏、主用户、非 fallback、非负设备编号且非外接设备事件。过期、不同时按下、取消和重复事件不触发。
- 三键启用时跳过 `PhoneWindowManager$7.execute` 的双键截图；`$8/$9` 的无障碍和电源/静音组合动作在识别窗口内延后，三键成功或原生 cancel 时取消待执行任务。单键检测器按原生组合键的方式 reset。成功后在策略 Handler 上调用 `handleScreenShot(1,2)`，使用普通系统截图入口而非模拟双键。
- 截屏设置使用 `ScreenshotSettingFragment.onResume` 注入。原双键键值位于 Settings.System，新三键键值位于 Settings.Secure；开启三键先关闭双键，双键原生监听器外包装关闭三键。底层检测同时检查双键关闭，外部重新开启双键时不会同时启用两种模式。
- Preference 的 `p/z/y/x/x0/G0/D0/C0/A0`、字段 `t`，PreferenceGroup 的 `P0/T0/S0/O0` 及 TwoStatePreference 的 `O0` 均已通过 DEX 声明核验；旧无线设置 APK 的未混淆方法名不复用。
- `tools/verify-apk-hooks.py` 增加截屏 18 个、services 17 个、oplus-services 3 个目标核验。`handleKeyGesture` 返回后恢复已消费电源键的 handled 标记，避免电源键最后按下时原生相机手势判断覆盖该标记。
- 单元测试覆盖六种顺序、重复与松键、超时、取消、不同设备、再次触发、默认关闭及开关互斥；不能代替手机上的 ART Hook 和输入线程调度验收。

## 构建号 7：内置输入设备拆分与诊断

用户反馈开关存在但按键不截图，确认已勾选系统框架并重启。检查发现构建号 6 强制三个键来自同一 deviceId；这不适用于电源键与音量键由不同内置驱动上报的设备。现改为每个键独立记录 deviceId 与 downTime，允许跨内置设备组合，忽略外接设备；无关设备或旧轮次的松键不能清除当前按住状态。此问题是否就是用户手机的根因，仍需设备日志确认。

诊断日志覆盖初始化、实际配置值、有限次数的三个目标键事件、识别成功、请求发送与异常。回归测试新增跨驱动触发及错误松键隔离。

## 构建号 8：父类私有方法调用修复

用户提供的 `LSPosed_20261024_161532.zip` 与 `LSPosed_20261024_161516.zip` 显示配置为 `enabled=true triple=1 dual=0`，三个按键均来自 device=2，已记录 `Three-key chord recognized`。随后报 `NoSuchMethodError: com.android.server.policy.SubPhoneWindowManager#cancelPendingScreenshotChordAction[]`，没有发送截图请求。构建号 7 的设备编号调整不是此手机故障的根因。

通过子类对象调用 XposedHelpers.callMethod 无法查找到父类的私有方法。改由 ScreenshotPolicyActions 从声明类 PhoneWindowManager 精确解析并开放访问四个取消方法及 handleScreenShot，安装 Hook 前完成所有解析，运行时在实际子类实例上调用缓存的 Method。回归测试验证私有父类调用、同名子类方法不会遮蔽截图入口，以及错误声明类在安装前失败。未将本地测试结果视为真机验证。

## 构建号 9：独立请求来源与松键状态

两份 20261024_1630 日志中，当前启动位于 log/，log.old/ 为上次启动，不可混用。16:29:46 三键识别并记录 request sent，上一版 NoSuchMethodError 已消失；随后两轮完整按键未再次识别。日志没有截屏应用明确的拒绝原因，以下冲突由所提供固件/APK 的调用链确认：

- DisplayPolicy 经厂商扩展创建 OplusDisplayPolicy$OplusScreenShotHandler，其 getSource 固定为 KeyPress。
- ScreenshotHelperExtImpl 丢弃整型 source，OplusScreenShotEuclidManager 根据 Handler 将来源写入 screenshot_source，再同步调用 OplusScreenshotManager.takeScreenshot(Bundle)。因此单改 handleScreenShot 的整型 source 无效。
- 截屏 APK 的 d6.i.d 识别 KeyPress，hc.c0.e 增加 hc.s（RejectPhysicalKeys），双键开关关闭时返回拒绝。三键与双键互斥使该路径必然冲突。
- 仅在 ScreenshotPolicyActions.takeScreenshot 同步调用内使用 ThreadLocal 标记，getSource Hook 返回 ConnectColoursThreeKeys。finally 清理标记，其他线程及其他原生请求保持原来源；hc.c0 默认分支保留通用拒绝检查，不改企业策略、锁屏或安全窗口限制。

按键状态此前要求松键 downTime 严格等于该键首次按下时间；现接受同设备且不早于首次按下的 downTime，同时分别保留原始按下与松开标识用于消费事件。测试覆盖共享最新 downTime 的连续三轮组合、提前松开、错误设备和旧时间松键；由于旧日志未记录 downTime，该因素对真机后续不识别的解释仍需新日志验证。新增 downTime 诊断。

回归测试也覆盖来源标记的线程隔离、正常清理与异常清理。新增厂商 Handler.getSource DEX 核验；未将构建及回归测试视为真机截图通过。
