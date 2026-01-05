// src/utils/visitorToken.ts
const LS_KEY = 'vtoken';
const COOKIE_KEY = 'vtoken';
const URL_KEY = 'vt';
const COOKIE_DAYS = 365;

function getCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}
function setCookie(name: string, val: string, days = COOKIE_DAYS) {
  const d = new Date();
  d.setTime(d.getTime() + days * 864e5);
  document.cookie = `${name}=${encodeURIComponent(val)}; path=/; expires=${d.toUTCString()}; samesite=lax`;
}

function readFromUrl(): string | null {
  const u = new URL(location.href);
  const vt = (u.searchParams.get(URL_KEY) || '').trim();
  return vt || null;
}
function writeToUrl(vt: string) {
  const u = new URL(location.href);
  u.searchParams.set(URL_KEY, vt);
  // 不刷新，仅替换地址栏，便于用户收藏/分享
  history.replaceState(null, '', u.toString());
}

export function createUUID() {
  // 尽量用加密随机；不支持时退化
  return (window.crypto?.randomUUID?.() ??
    ('v_' + Math.random().toString(36).slice(2) + Date.now().toString(36)));
}

/** 读取已有 token，优先顺序：URL -> localStorage -> cookie */
export function readVisitorToken(): string | null {
  return readFromUrl() || localStorage.getItem(LS_KEY) || getCookie(COOKIE_KEY);
}

/** 把 token 写入三处并广播 */
export function persistVisitorToken(vt: string) {
  try { localStorage.setItem(LS_KEY, vt); } catch {}
  try { setCookie(COOKIE_KEY, vt); } catch {}
  try { writeToUrl(vt); } catch {}
  try {
    // 跨标签页同步（同域）
    const bc = new BroadcastChannel('vtoken');
    bc.postMessage({ vt });
    bc.close();
  } catch {}
}

/** 获取稳定 token：若不存在则创建新的并持久化 */
export function ensureVisitorToken(): string {
  const existed = readVisitorToken();
  if (existed) return existed;
  const fresh = createUUID();
  persistVisitorToken(fresh);
  return fresh;
}

/** 监听其他标签页变更 */
export function listenVTokenChange(cb: (vt: string)=>void) {
  try {
    const bc = new BroadcastChannel('vtoken');
    bc.onmessage = (ev) => {
      const vt = ev?.data?.vt;
      if (typeof vt === 'string' && vt) cb(vt);
    };
    return () => bc.close();
  } catch {
    return () => {};
  }
}

/** 生成“跨浏览器/跨设备”可用的分享链接（把 vt 带到 URL 上） */
export function buildShareUrl(vt?: string) {
  const token = vt || readVisitorToken() || ensureVisitorToken();
  const u = new URL(location.href);
  u.searchParams.set(URL_KEY, token);
  return u.toString();
}
