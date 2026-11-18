#!/usr/bin/env bash
set -euo pipefail
umask 077
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
test_serial="${1:?Usage: scripts/run-device-tests.sh <adb-serial>}"
test_output="${DAODIAN_DEVICE_TEST_OUTPUT_DIR:-$repo_root/.local/device-tests-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$test_output"
test_installed=false
cleanup_test_package() {
    if [ "$test_installed" = true ]; then
        adb -s "$test_serial" uninstall com.daodian.clock.test > "$test_output/test-package-cleanup.log" 2>&1 || true
    fi
}
trap cleanup_test_package EXIT
python3 - "$test_serial" "$test_output/before.json" <<'PY'
import subprocess,re,json,sys,pathlib
r=subprocess.check_output(['adb','-s',sys.argv[1],'shell','am','broadcast','-a','com.daodian.clock.GET_STATE','-n','com.daodian.clock/.DebugReceiver'],text=True)
m=re.search(r'data="(.*)"\s*$',r,re.S)
state=json.loads(m.group(1)) if m else None
if state is not None and state.get('active') is not None:raise RuntimeError('当前任务正在执行，不能运行设备测试')
pathlib.Path(sys.argv[2]).write_text(json.dumps(state,ensure_ascii=False,indent=2))
PY
cd "$repo_root"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain > "$test_output/build.log" 2>&1
# -r retains app data and fails on a signing mismatch; there is no uninstall fallback.
adb -s "$test_serial" install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk > "$test_output/app-install.log"
adb -s "$test_serial" install -r --no-streaming app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$test_output/test-install.log"
test_installed=true
adb -s "$test_serial" shell am instrument -w -r com.daodian.clock.test/android.test.InstrumentationTestRunner > "$test_output/instrumentation.log"
python3 - "$test_serial" "$test_output" <<'PY'
import subprocess,re,json,sys,pathlib,time
folder=pathlib.Path(sys.argv[2]);log=(folder/'instrumentation.log').read_text()
if not re.search(r'OK \(\d+ tests?\)',log):raise RuntimeError('设备测试失败，请检查 instrumentation.log')
r=subprocess.check_output(['adb','-s',sys.argv[1],'shell','am','broadcast','-a','com.daodian.clock.GET_STATE','-n','com.daodian.clock/.DebugReceiver'],text=True)
m=re.search(r'data="(.*)"\s*$',r,re.S)
if not m:raise RuntimeError('测试后主 App 不可读取')
after=json.loads(m.group(1));before=json.loads((folder/'before.json').read_text())
if before is not None and before['permissions']['access'] and not after['permissions']['access']:
    base=['adb','-s',sys.argv[1],'shell'];target='com.daodian.clock/com.daodian.clock.WecomAccessService'
    enabled=subprocess.check_output(base+['settings','get','secure','enabled_accessibility_services'],text=True).strip()
    others=[x for x in ([] if enabled in ('null','') else enabled.split(':')) if x!=target]
    if others:subprocess.run(base+['settings','put','secure','enabled_accessibility_services',':'.join(others)],check=True)
    else:subprocess.run(base+['settings','delete','secure','enabled_accessibility_services'],check=True,capture_output=True)
    time.sleep(.3)
    subprocess.run(base+['settings','put','secure','enabled_accessibility_services',':'.join(others+[target])],check=True)
    for attempt in range(10):
        time.sleep(.3)
        data=subprocess.check_output(base+['am','broadcast','-a','com.daodian.clock.GET_STATE','-n','com.daodian.clock/.DebugReceiver'],text=True)
        found=re.search(r'data="(.*)"\s*$',data,re.S)
        if found:
            after=json.loads(found.group(1))
            if after['permissions']['access']:break
(folder/'after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2))
if before is not None:
    for key in ('records','plans','next','master','mail','executionMode','flexibleRule','permissions'):
        if before[key]!=after[key]:raise RuntimeError('设备测试改变了 '+key)
print('设备测试通过，主 App 和已有数据保留。')
PY
