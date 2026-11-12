# ColorOS 13 适配依据

以下记录来自用户提供 APK 的本地反编译与原始 DEX 核验，不代表真机测试。

## 输入

| APK | SHA-256 |
| --- | --- |
| SystemUI 13.01.21 | `ddecac922069517fff931c19855ad99bbb011e598f525aba50c0ff7bfe17a382` |
| 设置 13.0.5 | `8c4cda3d38a3cf540d883786c9e29a5bb3cae29aa839d60d124ddcc58be7acc3` |
| 软件更新 3.16.17 | `e328464e4ae4c8d0846692072657f8db108d4c04ac8d7f95c158e169ef7e7d15` |
| (U)SIM卡应用 13.2.50 | `498f7eaa30a5f4820ab6887dc128e994241d8d85d98aa614beb6e3082de83bb7` |
| 无线设置 13.8.1 | `4cb3ab2e5302d5ae6e33ccb499546da8e3eaba0b7f9cb5c49b49d3bf22ebd931` |

## 磁贴

`com.oplusos.systemui.qs.tiles.OplusCellularTile.handleUpdateState(QSTile.SignalState,Object)` 直接设置 `signalState.label`，并负责 SIM 卡、飞行模式、开关状态与图标。因此使用 after Hook 仅替换标题并追加无障碍说明。

`QSTileImpl.handleSetListening(boolean)` 控制监听生命周期；`OplusCellularTile.handleDestroy()` 负责销毁。后台统计任务独立于 SystemUI 主线程，结果投递回 `mHandler`；停止监听后取消定时刷新，销毁后丢弃在途结果。

原 SystemUI Manifest 声明 `READ_NETWORK_USAGE_HISTORY` 和 `PACKAGE_USAGE_STATS`，统计使用其宿主权限，而非给模块申请额外使用情况访问权限。

## 设置

- `OtaUpdateController.getAvailabilityStatus()` 对应 `key_ota_update`，即关于本机的整个 ColorOS 更新卡。
- `TopLevelOtaPreferenceController`、AOSP 的 `SystemUpdatePreferenceController` 与 `AdditionalSystemUpdatePreferenceController` 使用同样的可用状态契约；值 `3` 为不支持/不可用。
- `MessageEntryController.e0(Context)` 返回首页推荐卡列表。实体 `hd.a` 的原始字段 `k` 为来源包，`m` 为目标包，`n` 为目标 action；只剔除 OTA 条目。
- `xi.n3.p0(Context)` 读取 `oplus_customize_ota_patch`，是红点计数来源；`TopLevelAboutPhonePreferenceController.updatePreferenceMark(int)` 为关于本机红点的直接渲染入口。
- `SettingsSearchIndexablesProvider` 提供原始、动态搜索条目与不可索引键。通过目标包、action、精确 key 过滤，保留关于本机、普通版本信息和应用商店更新。

## 软件更新

- `b4.a.l(String,Object)` 是偏好读取入口。只对 `b4.d` 实例的 `update_state` 返回 `0`；不更改存储内容。
- `EntryActivity.Z0()` 将 `0` 放入空闲/无更新分支。
- `com.oplus.ota.service.h.handleMessage(Message)` 的 `1001` 为查询更新、`1002` 为查询描述；`2001` 为无更新、`2006` 为描述处理完成。重定向后走宿主自己的完成回调，避免一直转圈或报告断网。
- `v4.g.run()` 是查询任务。原始字段 `c` 为回调 Handler，`d` 为查询类型。替换任务，返回 `2001` 或 `2006`。
- `OTAProvider.call` 的 `callExternalGetOtaInfo` 和 `getOTAInfoFromAssistant` 返回 `Bundle.data` JSON；保留原权限检查，仅修改成功返回的数据。
- `c5.p.m(Context)` 显示 `dialog_download_title`（“发现系统新版本”）及 `notify_download_summery`（“优化了系统稳定性，带来更好的使用体验。”），与用户图 2 相符。
- `c5.p.k(Context)` 显示更新安装提示。两者均在构建弹窗前阻断。
- `c5.h` 使用通知 ID `88766`、`1987923`、`1987924`、`20020401`。仅抑制这些 OTA 通知，并在加载时取消旧通知。
- OTA 使用混淆后的 OkHttp `okhttp3.l0.b()` 进入请求拦截链；下载代码 `f4.b`、`f4.i`、`c4.p` 使用 `URL.openConnection()`。同时拦截 TCP `Socket.connect` 作为进程内兜底。

## SIM 欢迎弹窗（0.2.1）

- Manifest 包名为 `com.android.stk`，versionCode `13002050`，versionName `13.2.50`，Application 运行在 `com.android.phone` 共享进程。STK 的 Hook 在该包的加载回调中直接安装，不再等待可能已经发生的 `Application.attach`。
- `StkAppService` 不含旧实现依赖的 `RES_ID_BACKWARD` 字段。`StkDialogActivity` 的返回监听器直接使用 `21`，取消监听器调用 `sendResponse(13, false)`。
- `StkDialogActivity.onCreate` 完成消息、卡槽和上下文初始化后，将新建对话框保存到 `mAlertDialog`，最后调用 `show()`。实际对话框类 `androidx.appcompat.app.a` 经 `androidx.appcompat.app.c` 继承 `android.app.Dialog`，没有覆盖 `show()`。
- `h0.a.y` 使用两层 `ContextThemeWrapper` 包装 Activity。拦截 `Dialog.show` 时逐层解包，只接受 `StkDialogActivity`，并核对当前 Dialog 与该实例的 `mAlertDialog` 完全一致，避免影响共享电话进程的其他弹窗。
- 欢迎文案命中后调用原生 `sendResponse(13, false)`。该方法取消 AlarmManager 超时，检查卡槽及服务，并在 `responseNeeded` 为真时通过平台适配器发送包含原卡槽的响应，再调用 `finish()`。只有确认 Activity 正在结束，才跳过显示。
- 截图文案为“尊敬的客户：感谢您选择中国移动通信！”。新增精确规则接受空白及中英文冒号/叹号；不使用任意包含“感谢”的宽泛规则。
- DEX 检查核验 `mTextMsg`、`mAlertDialog`、`sendResponse(int,boolean)` 及 `onCreate(Bundle)`。`TextMessage.text` 和框架 `Dialog.show` 属于系统 API，不包含在这个 APK 的声明核验中。

## 蓝牙设备重连（0.2.2）

- 设置 13.0.5 的 `BluetoothPreferenceController` 实际跳转 `com.oplus.wirelesssettings`，不是设置 APK 中的 AOSP 详情页。
- 无线设置 13.8.1 的设备详情 Fragment 为 `t5.m`，原始 DEX 字段 `f` 是 `CachedBluetoothDevice`。在 `onResume()` 原逻辑结束后读取 `getAddress()` 并注入控件，检查现有 key 防止重复。
- 原生分类构造函数是 `COUIPreferenceCategory(Context,AttributeSet)`，没有只接受 Context 的构造函数。开关使用 `COUISwitchPreference(Context)`，次数选择使用 `COUIListPreference(Context)`；通过 `Preference$d.onPreferenceChange` 接收变化。关闭自动连接时隐藏次数项。
- `t5.m.onPreferenceTreeClick` 中 `key_disconnect` 调用 `N()`，进而调用 `CachedBluetoothDevice.disconnect()`。无线设置与 SystemUI 均核验了这一方法和 `getAddress()`，在原断开操作之前保存暂停状态。
- SystemUI Manifest 持有 `BLUETOOTH_CONNECT`、`BLUETOOTH_PRIVILEGED`。其主进程托管唯一重连调度器，其他 SystemUI 子进程不安装，避免多进程重复请求。
- 无线设置的 `CachedBluetoothDevice.connectDevice()` 使用系统的 `BluetoothDevice.connect()`；重连请求同样调用该接口，沿用系统现有连接策略，不强制打开所有 profile。系统 Android 13 运行库核验该方法返回 int，`isConnected()` 返回 boolean。
- 用户日志 `LSPosed_20261021_114353.zip` 的 `full.log` 在 11:43:40.141 明确记录 `OplusAppStartupManager: prevent start io.github.connectcolours ... by contentprovider com.oplus.wirelesssettings`，随后出现 `Unknown authority io.github.connectcolours.bluetooth`。这是旧版设备选项未显示的原因；用户开启启动权限后确认选项出现。
- versionCode 5 删除模块 ContentProvider。无线设置 Manifest 和 SystemUI APK 的 `aapt2 dump permissions` 均确认 `WRITE_SECURE_SETTINGS`，由两个宿主直接使用系统 SettingsProvider 的 `Settings.Secure` 存储专用条目。写权限由系统检查，模块 APK 不申请该权限，不再提供可调用的导出 Provider。API 依据：[Settings.Secure](https://developer.android.com/reference/android/provider/Settings.Secure)。
- 每设备开关与次数原子保存为一个有符号整数（正数开启，负数关闭，绝对值为 1～5），默认关闭且 1 次；暂停独立保存，避免设置页持有旧配置时覆盖刚发生的手动暂停。仅遍历已配对设备，不在模块进程保存或提供地址列表。
- 暂停标记绑定 `BOOT_COUNT` 和蓝牙会话值，手机重启自动失效；蓝牙 STATE_ON 更新会话值清除所有暂停。SystemUI 重启不会重置会话。取消配对删除对应配置和暂停。ContentObserver 只处理模块专用键和全表失效通知，忽略其他系统设置变化。
- 旧版私有配置不自动迁移，覆盖安装并重启后需要重新设置设备选项；模块功能开关仍使用原共享偏好。
- 轮次状态记录 15 秒等待、10 秒间隔、成功、次数耗尽、手动暂停。失败引起的 ACL_DISCONNECTED 不重置预算；曾连接的设备真正断开后才自动开始新轮。更改设备次数、重新开启设备选项会建立新轮；手动暂停仍只在蓝牙重新开启或手机重启后解除。
- 调度采用 `AlarmManager.setExact(ELAPSED_REALTIME_WAKEUP,...,OnAlarmListener,Handler)`，与该 SystemUI 自带 `AlarmTimeout` 的机制一致；全部设备轮次结束后取消定时，不常驻扫描。
- Toast 提示独立默认开启：每次连接请求前显示设备别名和进度；进入成功状态或首次进入次数耗尽状态时提示结果。中间失败、重复状态广播和用户主动暂停不产生失败提示。关闭提示不改变重连状态机。
- 原生图形模式的 Robolectric 渲染输出在 `.tooling/ui-0.2.2`，用于检查已激活、未激活和设置页布局。渲染中的设备信息属于测试环境。

## 验证边界

DEX 检查核验方法/字段的原始名称和签名，不能证明设备上的 ART Hook、界面呈现和权限实际运行成功。Robolectric 测试覆盖搜索结果过滤及非 OTA 条目保护；没有将其当作真实 LSPosed 环境测试。

versionCode 6 的三键截图功能新增 system_server 按键 Hook，仅对已核验的 `PHJ110_11_C.13` 固件启用；详见 `screenshot-adaptation.md`。模块不修改 hosts，不停止独立 update_engine/Recovery 中已经启动的更新。网络阻断限定软件更新应用中的已分析 Java 网络路径。
