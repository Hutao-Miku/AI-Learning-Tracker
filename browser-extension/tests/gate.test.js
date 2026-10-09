// gate.test.js — 用 node 模拟「学习模式」开关与悬浮窗门控逻辑
// 运行：node tests/gate.test.js
'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');

const contentSrc = fs.readFileSync(path.join(__dirname, '..', 'content.js'), 'utf8');

// 通用假元素工厂：满足 content.js 创建悬浮窗 / 通知所需的 DOM API
function fakeEl() {
  const el = {
    style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    children: [],
    checked: false,
    offsetWidth: 188,
    offsetHeight: 60,
    addEventListener() {},
    removeEventListener() {},
    appendChild(c) { this.children.push(c); return c; },
    setAttribute() {},
    getBoundingClientRect() { return { top: 10, left: 10, width: 188, height: 60 }; },
    querySelector() { return fakeEl(); },
    querySelectorAll() { return []; },
    closest() { return null; }
  };
  Object.defineProperty(el, 'innerHTML', { set() {}, get() { return ''; } });
  Object.defineProperty(el, 'textContent', { set() {}, get() { return ''; } });
  return el;
}

// 在一个干净的沙箱里加载 content.js，并模拟 chrome.storage / DOM
function runWithMode(modeOn) {
  const handlers = {};           // 记录视频元素上的事件监听
  let sendMessageCalls = 0;
  const consoleLines = [];

  const fakeVideo = {
    duration: 100,
    currentTime: 0,
    addEventListener: (ev, cb) => { handlers[ev] = cb; }
  };

  const sandbox = {
    console: {
      log: (...a) => consoleLines.push(['log', a.join(' ')]),
      warn: (...a) => consoleLines.push(['warn', a.join(' ')]),
      error: (...a) => consoleLines.push(['error', a.join(' ')])
    },
    setTimeout: () => {},
    location: { href: 'https://www.bilibili.com/video/BVtest123' },
    window: { innerWidth: 1280, innerHeight: 800, open() {} },
    document: {
      readyState: 'complete',
      title: '测试视频',
      head: fakeEl(),
      body: fakeEl(),
      documentElement: fakeEl(),
      getElementById: () => null,
      // 含 'video' 的选择器返回假视频（模拟 B站播放器已加载），其余返回 null
      querySelector: (sel) => (sel && sel.indexOf('video') >= 0 ? fakeVideo : null),
      querySelectorAll: () => [],
      addEventListener: () => {},
      removeEventListener: () => {},
      createElement: () => fakeEl()
    },
    chrome: {
      storage: {
        local: {
          get: (keys, cb) => cb({ studyMode: modeOn }),
          set: () => {}
        },
        onChanged: { addListener: () => {} }
      },
      runtime: {
        lastError: null,
        sendMessage: () => { sendMessageCalls++; },
        onMessage: { addListener: () => {} }
      }
    }
  };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(contentSrc, sandbox);

  return { handlers, sendMessageCalls, consoleLines };
}

let passed = 0;
let failed = 0;
function assert(cond, msg) {
  if (cond) { passed++; console.log('  ✓ ' + msg); }
  else { failed++; console.log('  ✗ ' + msg); }
}

// 场景 1：学习模式【关闭】→ 不应挂载任何视频监听，也不应发任何消息
console.log('场景1：学习模式关闭');
{
  const { handlers, sendMessageCalls } = runWithMode(false);
  assert(!handlers.timeupdate, '关闭时未挂载 timeupdate 监听');
  assert(!handlers.ended, '关闭时未挂载 ended 监听');
  assert(sendMessageCalls === 0, '关闭时未向后台发送任何出题请求');
}

// 场景 2：学习模式【开启】→ 应挂载播放器监听，等待进度触发
console.log('场景2：学习模式开启');
{
  const { handlers } = runWithMode(true);
  assert(handlers.timeupdate, '开启时已挂载 timeupdate 监听');
  assert(handlers.ended, '开启时已挂载 ended 监听');
}

console.log('\n结果：通过 ' + passed + ' 项，失败 ' + failed + ' 项');
process.exit(failed === 0 ? 0 : 1);
