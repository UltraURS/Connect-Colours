"""Verify hook targets against declared DEX members, without relying on jadx aliases."""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path


def dex_members(data):
    def u16(offset):
        return struct.unpack_from("<H", data, offset)[0]

    def u32(offset):
        return struct.unpack_from("<I", data, offset)[0]

    def uleb(offset):
        value = shift = 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
            shift += 7

    strings = []
    for index in range(u32(56)):
        offset = u32(u32(60) + index * 4)
        _, offset = uleb(offset)
        strings.append(data[offset:data.index(b"\0", offset)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    protos = []
    for index in range(u32(72)):
        offset = u32(76) + index * 12
        params = u32(offset + 8)
        args = "" if not params else "".join(
            types[u16(params + 4 + i * 2)] for i in range(u32(params)))
        protos.append("(" + args + ")" + types[u32(offset + 4)])
    method_ids = []
    for index in range(u32(88)):
        offset = u32(92) + index * 8
        method_ids.append(types[u16(offset)] + "->" + strings[u32(offset + 4)]
                          + protos[u16(offset + 2)])
    field_ids = []
    for index in range(u32(80)):
        offset = u32(84) + index * 8
        field_ids.append(types[u16(offset)] + "->" + strings[u32(offset + 4)]
                         + ":" + types[u16(offset + 2)])
    methods, fields, classes = set(), set(), set()
    for index in range(u32(96)):
        offset = u32(100) + index * 32
        classes.add(types[u32(offset)])
        pos = u32(offset + 24)
        if not pos:
            continue
        counts = []
        for _ in range(4):
            count, pos = uleb(pos)
            counts.append(count)
        for group, count in enumerate(counts):
            member_id = 0
            for _ in range(count):
                delta, pos = uleb(pos)
                member_id += delta
                _, pos = uleb(pos)  # access flags
                if group >= 2:
                    _, pos = uleb(pos)  # code offset
                    methods.add(method_ids[member_id])
                else:
                    fields.add(field_ids[member_id])
    return methods, fields, classes


TARGETS = {
    "launcher": [
        "Lcom/android/quickstep/views/OplusRecentsViewImpl;->applyLoadPlan(Ljava/util/ArrayList;)V",
        "Lcom/android/quickstep/views/OplusRecentsViewImpl;->getDockView()Lcom/oplus/quickstep/dock/DockView;",
        "Lcom/oplus/quickstep/dock/DockView;->applyLoadPlan(Ljava/util/ArrayList;)V",
        "Lcom/android/quickstep/views/RecentsView;->reloadIfNeeded()V",
        "Lcom/android/quickstep/views/RecentsView;->setOverviewStateEnabled(Z)V",
        "Lcom/android/quickstep/views/RecentsView;->showCurrentTask([Lcom/android/systemui/shared/recents/model/Task;)V",
        "Lcom/android/quickstep/views/RecentsView;->mTaskListChangeId:I",
        "Lcom/android/quickstep/util/GroupTask;->task1:Lcom/android/systemui/shared/recents/model/Task;",
        "Lcom/android/quickstep/util/GroupTask;->task2:Lcom/android/systemui/shared/recents/model/Task;",
        "Lcom/android/quickstep/util/GroupTask;-><init>(Lcom/android/systemui/shared/recents/model/Task;Lcom/android/systemui/shared/recents/model/Task;Lcom/android/launcher3/util/SplitConfigurationOptions$StagedSplitBounds;)V",
        "Lcom/android/systemui/shared/recents/model/Task;->getPackageName()Ljava/lang/String;",
        "Lcom/android/systemui/shared/recents/model/Task;->key:Lcom/android/systemui/shared/recents/model/Task$TaskKey;",
        "Lcom/android/systemui/shared/recents/model/Task$TaskKey;->userId:I",
        "Lcom/android/launcher/Launcher;->onNewIntent(Landroid/content/Intent;)V",
        "Lcom/android/launcher3/Launcher;->onNewIntent(Landroid/content/Intent;)V",
        "Lcom/android/launcher3/Workspace;->moveToDefaultScreen()V",
        "Lcom/android/launcher3/Workspace;->DEFAULT_PAGE:I",
        "Lcom/android/launcher3/OplusWorkspace;->moveToDefaultScreen()V",
    ],
    "sidebar": [
        "Lcom/coloros/edgepanel/api/SideBarProvider;->call(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;",
        "Lcom/coloros/edgepanel/utils/EdgePanelSettingsValueProxy;->getToggleState(Landroid/content/Context;)I",
        "Lcom/oplus/smartsidebar/permanent/models/DisplayDataManager;->a:Lcom/oplus/smartsidebar/permanent/models/DisplayDataManager;",
        "Lcom/oplus/smartsidebar/permanent/models/DisplayDataManager;->m()Lcom/oplus/smartsidebar/permanent/floatbar/FloatBarMainView;",
        "Lcom/oplus/smartsidebar/permanent/models/DisplayDataManager;->r(ZZ)V",
        "Lcom/oplus/smartsidebar/permanent/models/DisplayDataManager;->f()V",
        "Lcom/oplus/smartsidebar/permanent/floatbar/FloatBarMainView;->mGlobalBarShowType:Lcom/oplus/smartsidebar/utils/BarState;",
    ],
    "screenshot": [
        "Lcom/oplus/screenshot/setting/ScreenshotSettingFragment;->onResume()V",
        "Lcom/oplus/screenshot/setting/ScreenshotSettingFragment;->onPreferenceChange(Landroidx/preference/Preference;Ljava/lang/Object;)Z",
        "Lcom/coui/appcompat/preference/COUISwitchPreference;-><init>(Landroid/content/Context;)V",
        "Landroidx/preference/Preference;->p()Landroid/content/Context;",
        "Landroidx/preference/Preference;->z()Landroidx/preference/PreferenceGroup;",
        "Landroidx/preference/Preference;->y()I",
        "Landroidx/preference/Preference;->x()Landroidx/preference/Preference$b;",
        "Landroidx/preference/Preference;->x0(Ljava/lang/String;)V",
        "Landroidx/preference/Preference;->G0(Ljava/lang/CharSequence;)V",
        "Landroidx/preference/Preference;->D0(Ljava/lang/CharSequence;)V",
        "Landroidx/preference/Preference;->C0(I)V",
        "Landroidx/preference/Preference;->A0(Landroidx/preference/Preference$b;)V",
        "Landroidx/preference/Preference;->t:Z",
        "Landroidx/preference/PreferenceGroup;->P0(Ljava/lang/CharSequence;)Landroidx/preference/Preference;",
        "Landroidx/preference/PreferenceGroup;->T0()I",
        "Landroidx/preference/PreferenceGroup;->S0(I)Landroidx/preference/Preference;",
        "Landroidx/preference/PreferenceGroup;->O0(Landroidx/preference/Preference;)Z",
        "Landroidx/preference/TwoStatePreference;->O0(Z)V",
    ],
    "services": [
        "Lcom/android/server/am/ActivityManagerService;->systemReady(Ljava/lang/Runnable;Lcom/android/server/utils/TimingsTraceAndSlog;)V",
        "Lcom/android/server/am/ActivityManagerService;->mContext:Landroid/content/Context;",
        "Lcom/android/server/policy/PhoneWindowManager;->handleKeyGesture(Landroid/view/KeyEvent;Z)V",
        "Lcom/android/server/policy/PhoneWindowManager;->handleScreenShot(II)V",
        "Lcom/android/server/policy/PhoneWindowManager;->mPowerKeyHandled:Z",
        "Lcom/android/server/policy/PhoneWindowManager;->mCurrentUserId:I",
        "Lcom/android/server/policy/PhoneWindowManager;->mHandler:Landroid/os/Handler;",
        "Lcom/android/server/policy/PhoneWindowManager;->mSingleKeyGestureDetector:Lcom/android/server/policy/SingleKeyGestureDetector;",
        "Lcom/android/server/policy/SingleKeyGestureDetector;->reset()V",
        *["Lcom/android/server/policy/PhoneWindowManager;->" + method + "()V" for method in (
            "cancelPendingScreenshotChordAction", "cancelPendingAccessibilityShortcutAction",
            "cancelPendingRingerToggleChordAction", "cancelGlobalActionsAction")],
        *[f"Lcom/android/server/policy/PhoneWindowManager${number};->{method}()V"
          for number in (7, 8, 9) for method in ("execute", "cancel")],
    ],
    "oplus-services": [
        "Lcom/android/server/wm/OplusDisplayPolicy$OplusScreenShotHandler;->getSource()Ljava/lang/String;",
        "Lcom/android/server/policy/SubPhoneWindowManager;->init(Landroid/content/Context;Landroid/view/IWindowManager;Lcom/android/server/policy/WindowManagerPolicy$WindowManagerFuncs;)V",
        "Lcom/android/server/policy/SubPhoneWindowManager;->interceptKeyBeforeQueueing(Landroid/view/KeyEvent;I)I",
        "Lcom/android/server/policy/SubPhoneWindowManager;->interceptKeyBeforeDispatching(Landroid/os/IBinder;Landroid/view/KeyEvent;I)J",
    ],
    "wireless": [
        "Lt5/m;->onResume()V",
        "Lt5/m;->f:Lcom/android/settingslib/bluetooth/CachedBluetoothDevice;",
        "Lcom/android/settingslib/bluetooth/CachedBluetoothDevice;->getAddress()Ljava/lang/String;",
        "Lcom/android/settingslib/bluetooth/CachedBluetoothDevice;->disconnect()V",
        "Lcom/coui/appcompat/preference/COUIPreferenceCategory;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V",
        "Lcom/coui/appcompat/preference/COUISwitchPreference;-><init>(Landroid/content/Context;)V",
        "Lcom/coui/appcompat/preference/COUIListPreference;-><init>(Landroid/content/Context;)V",
        "Landroidx/preference/Preference;->setOnPreferenceChangeListener(Landroidx/preference/Preference$d;)V",
        "Landroidx/preference/Preference$d;->onPreferenceChange(Landroidx/preference/Preference;Ljava/lang/Object;)Z",
        "Landroidx/preference/Preference;->setVisible(Z)V",
        "Landroidx/preference/PreferenceGroup;->addPreference(Landroidx/preference/Preference;)Z",
        "Landroidx/preference/PreferenceGroup;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;",
        "Landroidx/preference/ListPreference;->setValue(Ljava/lang/String;)V",
        "Landroidx/preference/ListPreference;->setEntries([Ljava/lang/CharSequence;)V",
        "Landroidx/preference/ListPreference;->setEntryValues([Ljava/lang/CharSequence;)V",
        "Landroidx/preference/TwoStatePreference;->setChecked(Z)V",
    ],
    "stk": [
        "Lcom/android/stk/StkDialogActivity;->mTextMsg:Lcom/android/internal/telephony/cat/TextMessage;",
        "Lcom/android/stk/StkDialogActivity;->mAlertDialog:Landroidx/appcompat/app/a;",
        "Lcom/android/stk/StkDialogActivity;->sendResponse(IZ)V",
        "Lcom/android/stk/StkDialogActivity;->onCreate(Landroid/os/Bundle;)V",
    ],
    "systemui": [
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureConfiguration;->getAnimSwitchDistance()I",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureDetector;->edgeContains(Landroid/graphics/Region;III)Z",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureDetector;->mExcludeRegion:Landroid/graphics/Region;",
        "Lcom/oplusos/systemui/navigationbar/gesture/AppSettingsHandler;->disableBack()Z",
        "Lcom/oplusos/systemui/common/util/Util;->getTopActivityComponentName()Landroid/content/ComponentName;",
        "Lcom/oplusos/systemui/common/util/Util;->getFocusWindowPkgName()Ljava/lang/String;",
        *["Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureDetector;->" + method for method in (
            "onMotionEventImpl(Landroid/view/MotionEvent;)V", "switchAppDelay()V", "unregisterSmartSideBarRegion()V",
            "checkAllowGesture(II)Z", "mSysUiFlags:I", "mDisabledForQuickstep:Z", "mIsBackGestureAllowed:Z", "mGestureBlockingActivityRunning:Z",
            "mContext:Landroid/content/Context;", "mGestureUIHandler:Landroid/os/Handler;",
            "mTopActivity:Landroid/content/ComponentName;", "mIsOnLeftEdge:Z", "mAllowGesture:Z", "mThresholdCrossed:Z",
            "mDisplaySize:Landroid/graphics/Point;", "mDisplay:Landroid/view/Display;",
            "mSideGestureViewManager:Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureViewManager;")],
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureConfiguration;->getCancelDistance()I",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGesturePreProcess;->disableGestureInLauncher()Z",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGesturePreProcess;->mCurrentActivityName:Ljava/lang/String;",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGesturePreProcess;->mFocusWindowPkgName:Ljava/lang/String;",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureNavView;->onDraw(Landroid/graphics/Canvas;)V",
        "Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureNavView;->mBackIcon:Landroid/graphics/Bitmap;",
        *["Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureViewManager;->mNav" + side + "View:Lcom/oplusos/systemui/navigationbar/gesture/sidegesture/SideGestureNavView;" for side in ("Left", "Right")],
        "Lcom/android/settingslib/bluetooth/CachedBluetoothDevice;->disconnect()V",
        "Lcom/android/settingslib/bluetooth/CachedBluetoothDevice;->getAddress()Ljava/lang/String;",
        "Lcom/oplusos/systemui/qs/tiles/OplusCellularTile;->handleUpdateState(Lcom/android/systemui/plugins/qs/QSTile$SignalState;Ljava/lang/Object;)V",
        "Lcom/oplusos/systemui/qs/tiles/OplusCellularTile;->handleDestroy()V",
        "Lcom/android/systemui/qs/tileimpl/QSTileImpl;->handleSetListening(Z)V",
        "Lcom/android/systemui/qs/tileimpl/QSTileImpl;->refreshState()V",
        "Lcom/android/systemui/qs/tileimpl/QSTileImpl;->mContext:Landroid/content/Context;",
        "Lcom/android/systemui/qs/tileimpl/QSTileImpl;->mHandler:Lcom/android/systemui/qs/tileimpl/QSTileImpl$H;",
        "Lcom/android/systemui/plugins/qs/QSTile$State;->label:Ljava/lang/CharSequence;",
        "Lcom/android/systemui/plugins/qs/QSTile$State;->contentDescription:Ljava/lang/CharSequence;",
    ],
    "settings": [
        "Lcom/oplus/settings/feature/deviceinfo/controller/OtaUpdateController;->getAvailabilityStatus()I",
        "Lcom/oplus/settings/feature/deviceinfo/controller/TopLevelOtaPreferenceController;->getAvailabilityStatus()I",
        "Lcom/android/settings/system/SystemUpdatePreferenceController;->getAvailabilityStatus()I",
        "Lcom/android/settings/system/AdditionalSystemUpdatePreferenceController;->getAvailabilityStatus()I",
        "Lxi/n3;->p0(Landroid/content/Context;)I",
        "Lcom/oplus/settings/feature/homepage/TopLevelAboutPhonePreferenceController;->updatePreferenceMark(I)V",
        "Lcom/oplus/settings/feature/homepage/messageentry/MessageEntryController;->e0(Landroid/content/Context;)Ljava/util/List;",
        "Lhd/a;->k:Ljava/lang/String;",
        "Lhd/a;->m:Ljava/lang/String;",
        "Lhd/a;->n:Ljava/lang/String;",
        *["Lcom/android/settings/search/SettingsSearchIndexablesProvider;->"
          + method + "([Ljava/lang/String;)Landroid/database/Cursor;"
          for method in ("queryRawData", "queryDynamicRawData", "queryNonIndexableKeys")],
    ],
    "ota": [
        "Lb4/d;",
        "Lb4/a;->l(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
        "Lcom/oplus/ota/service/h;->handleMessage(Landroid/os/Message;)V",
        "Lv4/g;->run()V",
        "Lv4/g;->c:Landroid/os/Handler;",
        "Lv4/g;->d:Ljava/lang/String;",
        "Lcom/oplus/ota/db/OTAProvider;->call(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;",
        "Lc5/p;->k(Landroid/content/Context;)V",
        "Lc5/p;->m(Landroid/content/Context;)V",
        "Lokhttp3/l0;->b()Lokhttp3/r0;",
    ],
}


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    apk_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else root / ".tooling/apks"
    results = {}
    for name, expected in TARGETS.items():
        path = (root / "framework" / (name + ".jar")) if name in ("services", "oplus-services") else apk_dir / (name + ".apk")
        declared = set()
        with zipfile.ZipFile(path) as archive:
            for entry in archive.namelist():
                if entry.endswith(".dex"):
                    for members in dex_members(archive.read(entry)):
                        declared.update(members)
        missing = set(expected) - declared
        if missing:
            raise SystemExit(name + " missing DEX targets:\n" + "\n".join(sorted(missing)))
        results[name] = {
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "verifiedTargets": len(expected),
        }
    output = root / ".tooling/hook-verification.json"
    output.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))
