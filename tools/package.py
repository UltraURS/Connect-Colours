"""Package the checked debug APK and its verification metadata."""
import hashlib
import json
import runpy
import shutil
import subprocess
import zipfile
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1]
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
build_tools = root.parent / "xbst enhance/.tooling/android-sdk/build-tools/35.0.0"
dex_members = runpy.run_path(str(root / "tools/verify-apk-hooks.py"))["dex_members"]

reports = list((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert reports, "Missing test results"
tests = 0
android13_tests = 0
for report in reports:
    suite = ElementTree.parse(report).getroot()
    assert int(suite.attrib["failures"]) == 0 and int(suite.attrib["errors"]) == 0
    assert int(suite.attrib["skipped"]) == 0
    tests += int(suite.attrib["tests"])
    if suite.attrib["name"] not in ("io.github.connectcolours.hook.SimWelcomeHookTest",
                                   "io.github.connectcolours.bluetooth.RetryRoundTest",
                                   "io.github.connectcolours.bluetooth.ReconnectMessagesTest",
                                   "io.github.connectcolours.screenshot.TripleChordTest",
                                   "io.github.connectcolours.screenshot.ScreenshotPolicyActionsTest",
                                   "io.github.connectcolours.sidebar.SidebarGestureTest",
                                   "io.github.connectcolours.recents.RecentTaskFilterTest"):
        android13_tests += int(suite.attrib["tests"])
issues = ElementTree.parse(root / "app/build/reports/lint-results-debug.xml").getroot()
assert not any(issue.get("severity") in ("Error", "Fatal") for issue in issues)

with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    assert archive.read("assets/xposed_init").decode().strip() == "io.github.connectcolours.hook.ModuleEntry"
    classes = set()
    for name in archive.namelist():
        if name.endswith(".dex"):
            classes.update(dex_members(archive.read(name))[2])
    for name in ("ModuleEntry", "TrafficTileHook", "SettingsHook", "OtaHook", "SimWelcomeHook",
                 "FeatureConfig", "BluetoothSettingsHook", "BluetoothReconnectHook",
                 "ScreenshotKeysHook", "ScreenshotSettingsHook", "SidebarGestureHook", "SidebarProviderHook", "SidebarRelayHook", "HiddenRecentsHook"):
        assert f"Lio/github/connectcolours/hook/{name};" in classes
    assert "Lio/github/connectcolours/MainActivity;" in classes
    assert "Lio/github/connectcolours/bluetooth/BluetoothConfig;" in classes
    assert "Lio/github/connectcolours/bluetooth/BluetoothConfigProvider;" not in classes
    for prefix in ("Lde/robv/android/xposed/", "Lorg/junit/", "Lorg/robolectric/"):
        assert not any(name.startswith(prefix) for name in classes), prefix

signature = subprocess.run(
    [str(build_tools / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(apk)],
    capture_output=True, check=True)
alignment = subprocess.run(
    [str(build_tools / "zipalign.exe"), "-c", "-v", "4", str(apk)],
    capture_output=True, check=True)
dist = root / "dist"
dist.mkdir(exist_ok=True)
target = dist / "Connect-Colours-0.2.4-debug.apk"
shutil.copy2(apk, target)
(dist / "apk-signature.txt").write_bytes(signature.stdout)
(dist / "apk-alignment.txt").write_bytes(alignment.stdout)
sha = hashlib.sha256(target.read_bytes()).hexdigest()
info = {
    "name": "Connect Colours",
    "description": "适用于Color OS13的系统改造模块。",
    "applicationId": "io.github.connectcolours",
    "version": "0.2.4",
    "versionCode": 17,
    "variant": "debug",
    "apk": target.name,
    "bytes": target.stat().st_size,
    "sha256": sha,
    "validation": {
        "unitTests": tests,
        "android13UnitTests": android13_tests,
        "lintErrors": 0,
        "lintWarnings": sum(issue.get("severity") == "Warning" for issue in issues),
        "signatureVerified": True,
        "alignmentVerified": True,
        "xposedEntryVerified": True,
        "xposedApiNotPackaged": True,
        "deviceTested": False,
        "stkApkVerified": True,
        "wirelessApkVerified": True,
        "launcherApkVerified": True,
        "hiddenRecentsStorage": "LSPosed shared preferences; reload on recents entry",
        "bluetoothStorage": "system Settings.Secure (no module provider)",
    },
    "hookTargets": json.loads((root / ".tooling/hook-verification.json").read_text()),
}
(dist / "build-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(dist / "SHA256SUMS.txt").write_text(sha + "  " + target.name + "\n", encoding="utf-8")
shutil.copy2(root / "README.md", dist / "使用说明.md")
print(str(target))
print("SHA-256: " + sha)
