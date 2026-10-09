// content.js — 注入到 B站视频页 (*://*.bilibili.com/video/*)
// 1) 注入可拖拽的「学习追踪」悬浮窗（含学习模式开关 / 关闭按钮）
// 2) 学习模式开启时，监听播放器进度，达到 95% 或播放结束时，抓取内容并出题
// 学习模式关闭时：悬浮窗置灰半透明、且不监听、不抓取、不调后端、不弹窗

(function () {
  'use strict';

  // 后端连接失败时的统一提示文案
  const CONN_FAIL_MSG = '后端连接失败，请确认后端已启动且端口未被占用';

  // 把后端报错统一翻译为用户能看懂的中文（兜底，正常情况后端已返回友好文案）
  function friendlyError(msg) {
    const s = String(msg || '');
    if (/429|Too Many Requests|访问量过大|1305|繁忙/.test(s)) {
      return 'AI 服务器当前繁忙，请稍后重试';
    }
    if (/timeout|timed out|超时/i.test(s)) {
      return 'AI 接口响应超时，请稍后重试';
    }
    return s || '请确认后端正常运行';
  }

  // 防止重复触发（同一视频只出一次题）
  let fired = false;
  let currentBvid = getBvid();

  // 「学习模式」开关状态（默认关闭；只有开启才执行出题逻辑）
  let studyMode = false;
  let started = false; // 是否已挂载播放器监听

  // 悬浮窗 DOM 引用
  let widgetEl = null;

  // ---- 工具：获取 BV 号 ----
  function getBvid() {
    const m = location.href.match(/BV[0-9A-Za-z]+/);
    return m ? m[0] : null;
  }

  // ---- 工具：获取视频标题 ----
  function getTitle() {
    const el =
      document.querySelector('#video-title') ||
      document.querySelector('h1.video-title') ||
      document.querySelector('.video-info-title') ||
      document.querySelector('.video-title');
    if (el && el.innerText && el.innerText.trim()) return el.innerText.trim();
    return (document.title || '').replace(/_哔哩哔哩_bilibili.*$/, '').replace(/_bilibili.*$/i, '').trim();
  }

  // ---- 素材抓取：优先 AI 视频总结，逐级兜底 ----
  function getAiSummaryText() {
    const selectors = [
      '.video-ai-summary', '#ai-summary', '.ai-summary',
      '[class*="ai-summary"]', '[class*="AiSummary"]', '[class*="summary"]'
    ];
    for (const sel of selectors) {
      const el = document.querySelector(sel);
      if (el && el.innerText && el.innerText.trim().length > 0) return el.innerText.trim();
    }
    const headings = document.querySelectorAll('h2, h3, .title, [class*="title"], [class*="header"]');
    for (const h of headings) {
      if (h.textContent && h.textContent.includes('AI视频总结') && h.parentElement) {
        const txt = h.parentElement.innerText.trim();
        if (txt && txt.length > 0) return txt;
      }
    }
    return null;
  }

  function getDescription() {
    const el =
      document.querySelector('.desc-info .desc-info-text') ||
      document.querySelector('.desc-text') ||
      document.querySelector('.video-desc');
    if (el && el.innerText && el.innerText.trim()) return el.innerText.trim();
    const meta = document.querySelector('meta[name="description"]');
    if (meta) return meta.getAttribute('content');
    return null;
  }

  function getDanmaku() {
    const els = document.querySelectorAll(
      '[class*="danmaku"] .text, .dm-list .dm-text, .bili-danmaku .text, [class*="dm-list"] [class*="text"]'
    );
    const arr = Array.from(els).slice(0, 20).map((e) => (e.textContent || '').trim()).filter(Boolean);
    return arr.length ? arr.join('；') : null;
  }

  function getComments() {
    const els = document.querySelectorAll('.reply-item .reply-content, .comment-item .content');
    const arr = Array.from(els).slice(0, 5).map((e) => (e.textContent || '').trim()).filter(Boolean);
    return arr.length ? arr.join('\n') : null;
  }

  function gatherContent() {
    let text = getAiSummaryText();
    let source = 'AI视频总结';
    if (!text) { text = getDescription(); source = '视频简介'; }
    if (!text) { text = getDanmaku(); source = '热门弹幕'; }
    if (!text) { text = getComments(); source = '评论'; }
    return { text, source };
  }

  // ---- 查找视频元素 ----
  function getVideo() {
    return (
      document.querySelector('.bpx-player-video-wrap video') ||
      document.querySelector('.bilibili-player-video video') ||
      document.querySelector('#bilibili-player video') ||
      document.querySelector('video')
    );
  }

  function attachToVideo() {
    const video = getVideo();
    if (!video) { setTimeout(attachToVideo, 1000); return; }
    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('ended', trigger);
    console.log('[AI追踪] 已挂载到播放器，监听进度');
  }

  function onTimeUpdate() {
    if (!studyMode) return; // 中途关闭开关立刻停止
    const video = getVideo();
    if (!video || !video.duration) return;
    const bvid = getBvid();
    if (bvid && bvid !== currentBvid) { currentBvid = bvid; fired = false; }
    const pct = (video.currentTime / video.duration) * 100;
    if (pct >= 95) trigger();
  }

  function trigger() {
    if (!studyMode) return; // 关闭状态直接退出
    if (fired) return;
    fired = true;

    const bvid = getBvid();
    const title = getTitle();
    const { text, source } = gatherContent();

    if (!bvid) { console.warn('[AI追踪] 未能解析 BV 号，跳过'); return; }
    if (!text) {
      console.warn('[AI追踪] 未能抓取到任何视频内容（简介/总结/弹幕/评论均为空），跳过');
      showFallbackHint();
      return;
    }

    console.log('[AI追踪] 触发出题：', bvid, '标题=', title, '素材来源=', source);

    chrome.runtime.sendMessage(
      { type: 'GENERATE', payload: { bvid, title, text, source } },
      (resp) => {
        if (chrome.runtime.lastError) {
          console.error('[AI追踪] 与后台通信失败：', chrome.runtime.lastError.message);
          showError(CONN_FAIL_MSG);
          return;
        }
        if (resp && resp.success) {
          showNotification(title);
        } else if (resp && resp.connectionError) {
          showError(CONN_FAIL_MSG);
        } else {
          const msg = resp && resp.message ? friendlyError(resp.message) : '请确认后端正常运行';
          showError('出题失败：' + msg);
        }
      }
    );
  }

  // ---- 页面右上角通知（出题成功） ----
  function showNotification(title) {
    if (document.getElementById('ai-track-toast')) return;
    const box = document.createElement('div');
    box.id = 'ai-track-toast';
    box.style.cssText =
      'position:fixed;top:16px;right:16px;z-index:2147483647;max-width:320px;' +
      'background:#409eff;color:#fff;padding:14px 18px;border-radius:10px;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.3);cursor:pointer;font-size:14px;' +
      'font-family:system-ui,-apple-system,sans-serif;line-height:1.5;';
    box.innerHTML =
      '<div style="font-weight:600;margin-bottom:4px;">AI 已出好题 🎉</div>' +
      '<div>' + escapeHtml(title || '') + '<br/><span style="text-decoration:underline;">点击去作答 →</span></div>';
    box.addEventListener('click', () => { window.open('http://localhost:5173/quiz', '_blank'); box.remove(); });
    document.body.appendChild(box);
    setTimeout(() => { if (box.parentNode) box.remove(); }, 15000);
  }

  function showError(msg) {
    if (document.getElementById('ai-track-err')) return;
    const text = (msg == null) ? 'AI 出题提示：未知错误' : 'AI 出题提示：' + msg;
    const box = document.createElement('div');
    box.id = 'ai-track-err';
    box.style.cssText =
      'position:fixed;top:16px;right:16px;z-index:2147483647;max-width:320px;' +
      'background:#f56c6c;color:#fff;padding:12px 16px;border-radius:10px;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.3);font-size:13px;' +
      'font-family:system-ui,-apple-system,sans-serif;line-height:1.5;';
    box.textContent = text;
    document.body.appendChild(box);
    setTimeout(() => { if (box.parentNode) box.remove(); }, 8000);
  }

  function showFallbackHint() {
    showError('未能抓取到视频内容，请确认视频已加载，或在页面有简介/AI总结后再观看至 95%。');
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  // ============================================================
  // 悬浮窗（学习追踪）
  // ============================================================
  const WIDGET_CSS =
    '#ai-track-widget{position:fixed;top:16px;right:16px;z-index:2147483647;width:188px;' +
    'background:#fff;border-radius:12px;box-shadow:0 6px 24px rgba(0,0,0,.25);' +
    'font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#1f2329;' +
    'user-select:none;overflow:hidden;transition:opacity .2s;}' +
    '#ai-track-widget.ai-off{opacity:.55;}' +
    '#ai-track-widget.ai-off .ai-widget-header{background:#f0f0f0;color:#909399;}' +
    '#ai-track-widget.ai-on .ai-widget-header{background:#409eff;color:#fff;}' +
    '#ai-track-widget .ai-widget-header{display:flex;align-items:center;justify-content:space-between;' +
    'padding:9px 12px;cursor:move;background:#f5f7fa;border-bottom:1px solid #ebeef5;}' +
    '#ai-track-widget .ai-widget-title{font-size:13px;font-weight:700;}' +
    '#ai-track-widget .ai-widget-close{border:none;background:transparent;cursor:pointer;' +
    'font-size:16px;line-height:1;color:#909399;padding:0 2px;}' +
    '#ai-track-widget .ai-widget-body{display:flex;align-items:center;justify-content:space-between;padding:12px;}' +
    '#ai-track-widget .ai-widget-label{font-size:12px;}' +
    '.ai-widget-switch{position:relative;width:40px;height:22px;cursor:pointer;flex:0 0 auto;}' +
    '.ai-widget-switch input{opacity:0;width:0;height:0;}' +
    '.ai-widget-slider{position:absolute;inset:0;background:#dcdfe6;border-radius:22px;transition:.2s;}' +
    '.ai-widget-slider:before{content:"";position:absolute;height:16px;width:16px;left:3px;top:3px;' +
    'background:#fff;border-radius:50%;transition:.2s;}' +
    '.ai-widget-switch input:checked+.ai-widget-slider{background:#409eff;}' +
    '.ai-widget-switch input:checked+.ai-widget-slider:before{transform:translateX(18px);}';

  function injectStyle() {
    const style = document.createElement('style');
    style.textContent = WIDGET_CSS;
    (document.head || document.documentElement).appendChild(style);
  }

  function createWidget() {
    if (document.getElementById('ai-track-widget')) return;
    injectStyle();

    const widget = document.createElement('div');
    widget.id = 'ai-track-widget';
    widget.innerHTML =
      '<div class="ai-widget-header">' +
      '  <span class="ai-widget-title">学习追踪</span>' +
      '  <button class="ai-widget-close" title="关闭悬浮窗">×</button>' +
      '</div>' +
      '<div class="ai-widget-body">' +
      '  <span class="ai-widget-label">学习模式</span>' +
      '  <label class="ai-widget-switch">' +
      '    <input type="checkbox" id="ai-widget-study" />' +
      '    <span class="ai-widget-slider"></span>' +
      '  </label>' +
      '</div>';

    document.body.appendChild(widget);
    widgetEl = widget;

    const sw = widget.querySelector('#ai-widget-study');
    const closeBtn = widget.querySelector('.ai-widget-close');

    // 悬浮窗内的开关：写入 storage（popup 与 onChanged 双向同步）
    sw.addEventListener('change', () => {
      chrome.storage.local.set({ studyMode: sw.checked });
    });
    // 关闭按钮：仅隐藏（display:none），不销毁；并持久化隐藏状态
    closeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      chrome.storage.local.set({ widgetHidden: true }, () => applyHidden(true));
    });

    // 拖拽（仅在标题栏，且避开开关/关闭按钮）
    makeDraggable(widget, widget.querySelector('.ai-widget-header'));
    return widget;
  }

  function makeDraggable(widget, handle) {
    let dragging = false;
    let startX = 0, startY = 0, startLeft = 0, startTop = 0, moved = false;

    handle.addEventListener('mousedown', (e) => {
      // 不拦截开关 / 关闭按钮 / 任何输入框，避免拖拽误触发点击
      if (e.target.closest('.ai-widget-switch') ||
          e.target.closest('.ai-widget-close') ||
          e.target.closest('input')) return;
      e.preventDefault(); // 防止拖拽时选中页面文字
      dragging = true;
      moved = false;
      startX = e.clientX;
      startY = e.clientY;
      const rect = widget.getBoundingClientRect();
      startLeft = rect.left;
      startTop = rect.top;
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });

    function onMove(e) {
      if (!dragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true; // 防抖：超过阈值才算拖拽
      const newLeft = Math.max(0, Math.min(startLeft + dx, window.innerWidth - widget.offsetWidth));
      const newTop = Math.max(0, Math.min(startTop + dy, window.innerHeight - widget.offsetHeight));
      widget.style.left = newLeft + 'px';
      widget.style.top = newTop + 'px';
      widget.style.right = 'auto';
    }

    function onUp() {
      if (!dragging) return;
      dragging = false;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.userSelect = '';
      if (moved) {
        const rect = widget.getBoundingClientRect();
        chrome.storage.local.set({ widgetPos: { top: Math.round(rect.top), left: Math.round(rect.left) } });
      }
    }
  }

  function applyPos(pos) {
    if (!widgetEl) return;
    if (pos && typeof pos.top === 'number' && typeof pos.left === 'number') {
      widgetEl.style.top = pos.top + 'px';
      widgetEl.style.left = pos.left + 'px';
      widgetEl.style.right = 'auto';
    } else {
      widgetEl.style.top = '16px';
      widgetEl.style.right = '16px';
      widgetEl.style.left = 'auto';
    }
  }

  function applyHidden(hidden) {
    if (!widgetEl) return;
    widgetEl.style.display = hidden ? 'none' : 'block';
  }

  // 根据 studyMode 同步悬浮窗的开关勾选与高亮/置灰样式
  function applyWidgetState() {
    if (!widgetEl) return;
    const sw = widgetEl.querySelector('#ai-widget-study');
    if (sw) sw.checked = studyMode;
    widgetEl.classList.toggle('ai-on', studyMode);
    widgetEl.classList.toggle('ai-off', !studyMode);
  }

  // ---- 学习模式：真正开始监听视频进度（仅在学习模式开启时调用） ----
  function startListening() {
    if (started) return;
    started = true;
    console.log('[AI追踪] 学习模式已开启，准备监听视频进度');
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', attachToVideo);
    } else {
      attachToVideo();
    }
  }

  function applyStudyMode() {
    if (studyMode) startListening();
    else console.log('[AI追踪] 学习模式未开启，本页不监听视频、不出题');
  }

  function refreshStudyModeUI() {
    applyStudyMode();
    applyWidgetState();
  }

  // ---- 接收来自 popup 的「显示悬浮窗」指令 ----
  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg && msg.type === 'SHOW_WIDGET') {
      chrome.storage.local.set({ widgetHidden: false }, () => applyHidden(false));
      if (typeof sendResponse === 'function') sendResponse({ ok: true });
    }
  });

  // 1) 读取开关 / 位置 / 隐藏状态：关闭则直接 return（不监听、不抓取、不弹窗）
  chrome.storage.local.get(['studyMode', 'widgetPos', 'widgetHidden'], (res) => {
    studyMode = !!(res && res.studyMode);
    createWidget();
    applyPos(res ? res.widgetPos : null);
    applyHidden(!!(res && res.widgetHidden));
    refreshStudyModeUI();
  });

  // 2) 实时监听变化（看视频途中切换 / 跨面板同步 / 拖拽位置 / 显隐 都立刻生效）
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== 'local') return;
    if (changes.studyMode) {
      studyMode = !!(changes.studyMode.newValue);
      console.log('[AI追踪] 学习模式切换为：', studyMode ? '开启' : '关闭');
      refreshStudyModeUI();
    }
    if (changes.widgetPos) applyPos(changes.widgetPos.newValue);
    if (changes.widgetHidden) applyHidden(!!changes.widgetHidden.newValue);
  });
})();
