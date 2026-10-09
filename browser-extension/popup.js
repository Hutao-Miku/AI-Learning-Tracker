// popup.js — 插件图标点击后弹出的「学习模式」开关面板
'use strict';

document.addEventListener('DOMContentLoaded', init);

function init() {
  const toggle = document.getElementById('studyMode');
  const status = document.getElementById('status');
  const dot = document.getElementById('dot');

  // 读取已保存的开光状态（默认关闭）
  chrome.storage.local.get('studyMode', (res) => {
    const on = !!(res && res.studyMode);
    toggle.checked = on;
    renderStatus(on);
  });

  // 切换时保存到 chrome.storage.local（content.js 会通过 onChanged 实时感知）
  toggle.addEventListener('change', () => {
    const on = toggle.checked;
    chrome.storage.local.set({ studyMode: on }, () => {
      renderStatus(on);
    });
  });

  // 「显示悬浮窗」：把隐藏状态置为 false，并向当前 B站视频页的 content.js 发消息唤醒
  const showBtn = document.getElementById('showWidget');
  const tipEl = document.querySelector('.tip');
  showBtn.addEventListener('click', () => {
    chrome.storage.local.set({ widgetHidden: false }, () => {
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (!tabs || !tabs[0]) return;
        chrome.tabs.sendMessage(tabs[0].id, { type: 'SHOW_WIDGET' }, () => {
          if (chrome.runtime.lastError) {
            tipEl.textContent = '当前不是 B站视频页，无法显示悬浮窗。请先打开一个 B站视频。';
          } else {
            tipEl.textContent = '已发送显示指令，切回 B站视频页即可看到悬浮窗。';
          }
        });
      });
    });
  });

  function renderStatus(on) {
    status.textContent = on
      ? '当前：已开启（观看学习视频至 95% 将自动出题）'
      : '当前：未开启（不会自动出题）';
    status.classList.toggle('on', on);
    dot.classList.toggle('on', on);
  }
}
