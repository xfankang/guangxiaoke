package com.jianke.schedule.web

/**
 * 注入到课表页面里的抓取脚本。
 *
 * 页面结构（实测）：
 *   .Courses-head   -> 周一 ~ 周日
 *   .Courses-content > UL.stage_k > LI[j] > span.course-hasContent
 *       span 的 data-qsj = 起始节，height = 50px × 节数，背景色 = 学校配色
 *
 * 因为接口地址不可靠，翻周改成「点页面自己的 > 按钮」，由 App 侧一周一周驱动：
 *   __gxkScrape() 抓当前这一周   __gxkNav() 翻到下一周
 */
object ScheduleJs {

    const val BRIDGE_NAME = "GXKBridge"

    /**
     * 在页面内部一次性扫完整学期：
     * 先往后翻到学期末、再往回翻到第 1 周，每翻一周等页面更新后抓一次，
     * 全部扫完再合并成一份结果回传（比一周一次往返 App 快很多）。
     */
    val fullScanScript: String = """
(function () {
  var token = {};
  window.__gxkFullScanToken = token;
  function active() { return window.__gxkFullScanToken === token; }
  function report(payload) {
    if (!active()) { return; }
    try { window.GXKBridge.post('fullscan', String(payload)); } catch (e) { }
  }
  function txt(el) {
    if (!el) { return ''; }
    var t = el.innerText || el.textContent || '';
    return t.replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').trim();
  }
  function styleHeight(style) {
    var m = /height:\s*([0-9.]+)px/.exec(style || '');
    return m ? parseFloat(m[1]) : 0;
  }
  function styleColor(style) {
    var m = /background-color:\s*(rgb\([^)]*\))/.exec(style || '');
    return m ? m[1] : '';
  }
  function weekNo() {
    var m = /第\s*([0-9]{1,2})\s*周/.exec(document.body ? (document.body.innerText || '') : '');
    return m ? parseInt(m[1], 10) : 0;
  }
  function grab() {
    var content = document.querySelector('.Courses-content');
    if (!content) { return null; }
    var out = [];
    var kids = content.children;
    for (var i = 0; i < kids.length; i++) {
      var ul = kids[i];
      if (String(ul.tagName).toUpperCase() !== 'UL') { continue; }
      var lis = ul.children;
      for (var j = 0; j < lis.length; j++) {
        var span = lis[j].querySelector ? lis[j].querySelector('.course-hasContent') : null;
        if (!span) { continue; }
        var t = txt(span);
        if (!t) { continue; }
        var st = span.getAttribute('style') || '';
        out.push({
          t: t,
          d: j,
          p: i,
          q: span.getAttribute('data-qsj') || '',
          h: styleHeight(st),
          col: styleColor(st)
        });
      }
    }
    return out;
  }
  function findNav(dir) {
    function nearWeek(el) {
      var p = el.parentElement;
      for (var d = 0; d < 4 && p; d++) {
        if (/第\s*[0-9]+\s*周/.test(p.innerText || '')) { return true; }
        p = p.parentElement;
      }
      return false;
    }
    var all = document.querySelectorAll('div,span,a,i,button');
    for (var i = 0; i < all.length; i++) {
      var e = all[i];
      if (e.children && e.children.length > 0) { continue; }
      if (txt(e) !== dir) { continue; }
      if (!nearWeek(e)) { continue; }
      return e;
    }
    return null;
  }
  function fire(el) {
    try { el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); } catch (e) { }
  }
  var byWeek = {};
  var weekList = [];
  var guard = 0;
  var body0 = (document.body ? (document.body.innerText || '') : '').slice(0, 3000);
  function record() {
    var w = weekNo();
    if (!w || byWeek[w]) { return w; }
    var cells = grab();
    if (cells && cells.length > 0) {
      byWeek[w] = cells;
      weekList.push(w);
      report('progress:' + w);
    } else {
      byWeek[w] = [];
      weekList.push(w);
    }
    return w;
  }
  function step(dir, done) {
    if (!active()) { return; }
    guard++;
    if (guard > 60) { done(); return; }
    var btn = findNav(dir);
    if (!btn) { done(); return; }
    var before = weekNo();
    fire(btn);
    var tries = 0;
    var timer = setInterval(function () {
      if (!active()) { clearInterval(timer); return; }
      tries++;
      var now = weekNo();
      if (now && now !== before) {
        clearInterval(timer);
        record();
        setTimeout(function () { if (active()) { step(dir, done); } }, 80);
      } else if (tries > 25) {
        clearInterval(timer);
        done();
      }
    }, 120);
  }
  setTimeout(function () {
    if (!active()) { return; }
    record();
    step('>', function () {
      if (!active()) { return; }
      step('<', function () {
        if (!active()) { return; }
        var courses = {};
        var order = [];
        for (var i = 0; i < weekList.length; i++) {
          var w = weekList[i];
          var cells = byWeek[w] || [];
          for (var k = 0; k < cells.length; k++) {
            var c = cells[k];
            var lines = c.t.split('\n');
            var name = (lines[0] || '').trim();
            if (name.length < 2) { continue; }
            var room = (lines[1] || '').trim();
            var start = parseInt(c.q, 10);
            if (!start || start < 1) { start = c.p + 1; }
            var key = name + '|' + room + '|' + c.d + '|' + start + '|' + c.h;
            if (!courses[key]) {
              courses[key] = { n: name, r: room, d: c.d + 1, p: start, h: c.h, col: c.col, w: [] };
              order.push(key);
            }
            if (courses[key].w.indexOf(w) < 0) { courses[key].w.push(w); }
          }
        }
        var list = [];
        for (var i = 0; i < order.length; i++) { list.push(courses[order[i]]); }
        report('done:' + JSON.stringify({
          weeks: weekList.sort(function (a, b) { return a - b; }),
          courses: list,
          body: body0
        }));
      });
    });
  }, 500);
})();
""".trimIndent()

    /** 抓取当前显示的这一周。 */
    val scrapeScript: String = """
(function () {
  function report(kind, payload) {
    try { window.GXKBridge.post(kind, String(payload)); } catch (e) { }
  }
  function txt(el) {
    if (!el) { return ''; }
    var t = el.innerText || el.textContent || '';
    t = t.replace(/\u00a0/g, ' ');
    t = t.replace(/[ \t]+/g, ' ');
    t = t.replace(/\n\s*\n+/g, '\n');
    return t.trim();
  }
  function styleHeight(style) {
    var m = /height:\s*([0-9.]+)px/.exec(style || '');
    return m ? parseFloat(m[1]) : 0;
  }
  function styleColor(style) {
    var m = /background-color:\s*(rgb\([^)]*\))/.exec(style || '');
    return m ? m[1] : '';
  }
  window.__gxkScrape = function () {
    var content = document.querySelector('.Courses-content');
    if (!content) {
      var best = null;
      var bestN = 0;
      var uls = document.querySelectorAll('ul');
      for (var i = 0; i < uls.length; i++) {
        if (uls[i].children.length > bestN) { bestN = uls[i].children.length; best = uls[i]; }
      }
      content = best ? best.parentElement : null;
    }
    var grid = null;
    if (content) {
      var stages = [];
      var kids = content.children;
      for (var i = 0; i < kids.length; i++) {
        var ul = kids[i];
        if (String(ul.tagName).toUpperCase() !== 'UL') { continue; }
        var cells = [];
        var lis = ul.children;
        for (var j = 0; j < lis.length; j++) {
          var li = lis[j];
          var span = li.querySelector('.course-hasContent');
          var st = span ? (span.getAttribute('style') || '') : '';
          cells.push({
            t: span ? txt(span) : '',
            h: styleHeight(st),
            q: span ? (span.getAttribute('data-qsj') || '') : '',
            col: styleColor(st)
          });
        }
        stages.push({ c: String(ul.className || ''), cells: cells });
      }
      var head = [];
      var hd = document.querySelectorAll('.Courses-head > *');
      for (var a = 0; a < hd.length; a++) { head.push(txt(hd[a])); }
      var dates = [];
      var dd = document.querySelectorAll('.rq-head > *');
      for (var b = 0; b < dd.length; b++) { dates.push(txt(dd[b])); }
      var hidden = {};
      var names = ['xnm_hide', 'xqm_hide', 'zs_hide', 'xxdm'];
      for (var c = 0; c < names.length; c++) {
        var el = document.getElementById(names[c]);
        if (el) { hidden[names[c]] = String(el.value || ''); }
      }
      grid = { head: head, dates: dates, hidden: hidden, contentH: content.offsetHeight || 0, stages: stages };
    }
    var bodyText = txt(document.body || document.documentElement);
    report('scrape', JSON.stringify({
      href: location.href,
      bodyText: bodyText.slice(0, 6000),
      grid: grid
    }));
  };
  window.__gxkScrape();
})();
""".trimIndent()

    /** 点页面自己的「下一周」。 */
    val navScript: String = navScriptFor(">")

    /** 点页面自己的「上一周」（用来补第 7 周之前的那几周）。 */
    val prevScript: String = navScriptFor("<")

    private fun navScriptFor(dir: String): String = """
(function () {
  function report(payload) {
    try { window.GXKBridge.post('nav', String(payload)); } catch (e) { }
  }
  function txt(e) { return (e.innerText || e.textContent || '').trim(); }
  function nearWeek(el) {
    var p = el.parentElement;
    for (var d = 0; d < 4 && p; d++) {
      if (/第\s*[0-9]+\s*周/.test(p.innerText || '')) { return true; }
      p = p.parentElement;
    }
    return false;
  }
  var DIR = '${dir}';
  function fire(el) {
    // 一次导航只发一个 click；同时派发 touch/click 会让部分页面连续翻两周。
    try {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
    } catch (e) { }
  }
  var all = document.querySelectorAll('div,span,a,i,button');
  var target = null;
  for (var i = 0; i < all.length; i++) {
    var e = all[i];
    if (e.children && e.children.length > 0) { continue; }
    if (txt(e) !== DIR) { continue; }
    if (!nearWeek(e)) { continue; }
    target = e;
    break;
  }
  if (!target) { report(JSON.stringify({ ok: false, reason: 'no-next-button' })); return; }
  fire(target);
  report(JSON.stringify({ ok: true }));
})();
""".trimIndent()

    /** 兜底：切到「学期课表」。 */
    val semesterTabScript: String = """
(function () {
  var all = document.querySelectorAll('div,span,a,li');
  for (var i = 0; i < all.length; i++) {
    var e = all[i];
    if (e.children && e.children.length > 0) { continue; }
    if ((e.innerText || '').trim() !== '学期课表') { continue; }
    try { e.click(); } catch (err) { }
    return;
  }
})();
""".trimIndent()
}
