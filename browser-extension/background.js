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
