// background.js — Manifest V3 Service Worker
// 负责把 content.js 收集到的 B站视频数据 POST 到本地后端，并把结果回传。

const BACKEND_URL = 'http://localhost:8080/api/ai/generateFromBili';

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || message.type !== 'GENERATE') {
    return; // 不是我们关心的消息
  }

  const payload = message.payload || {};
  console.log('[AI追踪-background] 收到出题请求:', payload.bvid, '来源=', payload.source);

  fetch(BACKEND_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
    .then((resp) => resp.json())
    .then((data) => {
      const ok = data && data.code === 200;
      console.log('[AI追踪-background] 后端返回:', ok ? '成功' : '失败', data && data.message);
      sendResponse({ success: ok, message: data ? data.message : '无响应' });
    })
    .catch((err) => {
      // fetch 失败通常是后端未启动 / 端口被占用 / 无法连接
      console.error('[AI追踪-background] 请求异常（后端可能未启动或端口被占用）:', err);
      sendResponse({ success: false, connectionError: true, message: '后端连接失败' });
    });

  // 返回 true 表示异步调用 sendResponse，保持消息通道打开
  return true;
});

// ============================================================
// 自动关闭：当最后一个 localhost:5173 标签页被关闭时，通知后端优雅退出
// ============================================================

// 记录当前打开的「本项目」标签页（前端开发服务器地址）
const projectTabIds = new Set();

// 同时兼容 localhost 与 127.0.0.1
function isProjectUrl(url) {
  return typeof url === 'string' &&
    (url.startsWith('http://localhost:5173') || url.startsWith('http://127.0.0.1:5173'));
}

// 标签页加载 / 地址变化时，维护本项目标签页集合
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (isProjectUrl(tab.url)) {
    projectTabIds.add(tabId);
  } else if (projectTabIds.has(tabId)) {
    // 该标签页不再指向本项目（例如跳转到其他网站），移出集合
    projectTabIds.delete(tabId);
  }
});

// 标签页被关闭时
chrome.tabs.onRemoved.addListener((tabId) => {
  if (!projectTabIds.has(tabId)) return; // 不是本项目的标签页，忽略
  projectTabIds.delete(tabId);
  // 仅当这是最后一个本项目标签页时，才触发后端退出（避免多标签页时误关）
  if (projectTabIds.size === 0) {
    shutdownBackend();
  }
});

function shutdownBackend() {
  console.log('[AI追踪] 检测到最后一个项目标签页已关闭，请求后端优雅退出…');
  fetch('http://localhost:8080/api/system/shutdown', {
    method: 'POST',
    headers: { 'X-StudyTrace': '1' }
  })
    .then(() => console.log('[AI追踪] 后端关闭请求已发送'))
    .catch((err) => console.warn('[AI追踪] 后端关闭请求失败（可能后端已退出）：', err));
}
