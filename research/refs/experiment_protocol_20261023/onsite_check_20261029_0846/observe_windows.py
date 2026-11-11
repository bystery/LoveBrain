import ctypes, json, time
from pathlib import Path
u = ctypes.windll.user32
u.GetWindowThreadProcessId.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
u.GetWindowTextLengthW.argtypes = [ctypes.c_void_p]
u.GetWindowTextW.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_int]
u.IsWindowVisible.argtypes = [ctypes.c_void_p]
rows = []
seen = set()
cbtype = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
@cbtype
def visit(hwnd, _):
    if u.IsWindowVisible(hwnd):
        n = u.GetWindowTextLengthW(hwnd)
        b = ctypes.create_unicode_buffer(n + 1)
        u.GetWindowTextW(hwnd, b, n + 1)
        p = ctypes.c_ulong()
        u.GetWindowThreadProcessId(hwnd, ctypes.byref(p))
        key = (int(hwnd), p.value, b.value)
        if key not in seen:
            seen.add(key)
            rows.append(dict(time=time.strftime('%Y-%m-%dT%H:%M:%S%z'), hwnd=int(hwnd), pid=p.value, title=b.value))
    return True
end = time.monotonic() + 18
while time.monotonic() < end:
    u.EnumWindows(visit, 0)
    time.sleep(.1)
out = Path(__file__).with_name('VISIBLE_WINDOWS_BEFORE.json')
out.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps([r for r in rows if any(s in r['title'].lower() for s in ('python','cmd','powershell','campaign','anaconda'))], ensure_ascii=False))
