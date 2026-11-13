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
  // 页面「正在显示的周」的识别（注意：不是「日历上的当前周」，两个语义必须分开）：
  // 1. 结构提取（首选）：从「>/」翻页按钮所在的表头小块里取周次 —— 那一块只有
  //    当前显示的周，不受正文里下拉框文字的干扰。下拉框列出的全学期周次里，
  //    真实当前周（第 11 周）的日期罩着今天，跳到别的周时若按「今天在范围内」
  //    识别会把所有格子贴错周 —— 这是逐周扫描最容易翻车的地方。
  //    表头小块会被页面重渲染换掉：缓存节点只在 isConnected 且文本仍含周次时复用，
  //    失效就重新定位。
  // 2. 正文兜底（翻页按钮不在 DOM 里时）：
  //    a. 箭头紧邻的「第N周」（< 第3周 … > 的文本布局，同样锚定「显示中的周」）
  //    b. 今天落在日期范围内的行（此时显示的就是真实当前周）
  //    c. 第一行带日期的 → 最后宽松匹配第一个 1~30 的「第N周」
  var headerBox = null;
  function headerContainer() {
    if (headerBox && headerBox.isConnected && /第\s*[0-9]{1,2}\s*周/.test(headerBox.innerText || '')) {
      return headerBox;
    }
    var btn = findNav('>') || findNav('<');
    if (!btn) { return null; }
    var p = btn.parentElement;
    for (var d = 0; d < 4 && p; d++) {
      if (/第\s*[0-9]{1,2}\s*周/.test(p.innerText || '')) { headerBox = p; return headerBox; }
      p = p.parentElement;
    }
    return null;
  }
  function displayedWeekNo() {
    var box = headerContainer();
    if (box) {
      var mh = /第\s*([0-9]{1,2})\s*周/.exec(box.innerText || '');
      var wh = mh ? parseInt(mh[1], 10) : 0;
      if (wh >= 1 && wh <= 30) { return wh; }
    }
    return 0;
  }
  function weekNo() {
    var shown = displayedWeekNo();
    if (shown) { return shown; }
    var body = document.body ? (document.body.innerText || '') : '';
    var ma = /<\s*第\s*([0-9]{1,2})\s*周/.exec(body) || /第\s*([0-9]{1,2})\s*周\s*\n?\s*>/.exec(body);
    if (ma) {
      var wa = parseInt(ma[1], 10);
      if (wa >= 1 && wa <= 30) { return wa; }
    }
    var re = /第\s*([0-9]{1,2})\s*周\s*([0-9]{4})-([0-9]{2})-([0-9]{2})(?:\s*\/\s*([0-9]{4})-([0-9]{2})-([0-9]{2}))?/g;
    var m, first = 0;
    var now = new Date();
    var today = now.getFullYear() * 10000 + (now.getMonth() + 1) * 100 + now.getDate();
    while ((m = re.exec(body))) {
      var w = parseInt(m[1], 10);
      if (!(w >= 1 && w <= 30)) { continue; }
      if (!first) { first = w; }
      var d1 = parseInt(m[2], 10) * 10000 + parseInt(m[3], 10) * 100 + parseInt(m[4], 10);
      var d2 = m[5] ? parseInt(m[5], 10) * 10000 + parseInt(m[6], 10) * 100 + parseInt(m[7], 10) : d1;
      if (today >= d1 && today <= d2) { return w; }
    }
    if (first) { return first; }
    var re2 = /第\s*([0-9]{1,2})\s*周/g;
    var m2;
    while ((m2 = re2.exec(body))) {
      var w2 = parseInt(m2[1], 10);
      if (w2 >= 1 && w2 <= 30) { return w2; }
    }
    return 0;
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
  /** 量出「一行」的像素高度，并判断一行含 1 还是 2 小节（看 data-qsj 与行号的关系）。 */
  function metrics() {
    var content = document.querySelector('.Courses-content');
    if (!content) { return { unit: 0, perRow: 1 }; }
    var kids = content.children;
    var rows = 0;
    var one = 0;
    var two = 0;
    for (var i = 0; i < kids.length; i++) {
      var ul = kids[i];
      if (String(ul.tagName).toUpperCase() !== 'UL') { continue; }
      var lis = ul.children;
      for (var j = 0; j < lis.length; j++) {
        var sp = lis[j].querySelector ? lis[j].querySelector('.course-hasContent') : null;
        if (!sp) { continue; }
        var q = parseInt(sp.getAttribute('data-qsj'), 10);
        if (q > 0) {
          // 第 0 行没有区分度（两种公式都得 1），只看后面的行
          if (rows > 0) {
            var ratio = (q - 1) / rows;
            if (Math.abs(ratio - 2) < 0.25) { two++; } else if (Math.abs(ratio - 1) < 0.25) { one++; }
          }
          break;
        }
      }
      rows++;
    }
    var h = content.offsetHeight || 0;
    var perRow;
    if (one + two === 0) { perRow = (rows > 0 && rows <= 6) ? 2 : 1; }
    else { perRow = two > one ? 2 : 1; }
    return {
      unit: (h > 0 && rows > 0) ? h / rows : 0,
      perRow: perRow
    };
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
    // 只有「已经拿到过非空数据」的周才算记录过。空数组在 JS 里是真值，
    // 若首屏还没渲染完就注入脚本，当前周会被记成空——往后翻再回来时必须允许重录，
    // 否则这一周会在最终合并里永远缺课。
    var w = weekNo();
    if (!w || (byWeek[w] && byWeek[w].length)) { return w; }
    var cells = grab();
    if (cells && cells.length > 0) {
      byWeek[w] = cells;
      // 空周重录后 w 可能已经在 weekList 里，去重避免最终结果里同一周出现两次
      if (weekList.indexOf(w) < 0) { weekList.push(w); }
      report('progress:' + w);
    } else {
      byWeek[w] = [];
      if (weekList.indexOf(w) < 0) { weekList.push(w); }
    }
    return w;
  }
  // 页面网格还没渲染出来时等一等（就绪等待替代 App 侧的固定延时）：
  // 一出现立刻往下走，5 秒仍没有就照常执行，兜底行为与旧固定延时一致。
  function gxkReady(fn) {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (document.querySelector('.Courses-content') || document.querySelector('ul.stage_k')) {
        clearInterval(timer); fn();
      } else if (tries >= 100) { clearInterval(timer); fn(); }
    }, 50);
  }
  // 连续两次抓到的网格完全一致才算渲染稳定；空网格（没课的周）多等几拍再认。
  // 渲染中途读数会把格子记错周，宁可多等一两拍也不能录错。
  function grabStable(cb) {
    var prev = null, tries = 0;
    var timer = setInterval(function () {
      if (!active()) { clearInterval(timer); return; }
      tries++;
      var cells = grab();
      var ser = cells ? JSON.stringify(cells) : '';
      if (ser === prev && (ser !== '' || tries >= 6)) { clearInterval(timer); cb(); return; }
      prev = ser;
      if (tries >= 40) { clearInterval(timer); cb(); }
    }, 50);
  }
  // 跳转到目标周后的验证：周次标签已经变成目标、且网格稳定，才算跳成功。
  function waitStableWeek(target, cb) {
    var prev = null, tries = 0;
    var timer = setInterval(function () {
      if (!active()) { clearInterval(timer); return; }
      tries++;
      var now = weekNo();
      var cells = grab();
      var ser = cells ? JSON.stringify(cells) : '';
      if (now === target && ser === prev && (ser !== '' || tries >= 6)) {
        clearInterval(timer); cb(true); return;
      }
      prev = (now === target) ? ser : null;
      if (tries >= 60) { clearInterval(timer); cb(now === target); }
    }, 50);
  }
  var byWeek = {};
  var weekList = [];
  var guard = 0;
  var body0 = (document.body ? (document.body.innerText || '') : '').slice(0, 3000);
  function record() {
    // 只有「已经拿到过非空数据」的周才算记录过。空数组在 JS 里是真值，
    // 若首屏还没渲染完就注入脚本，当前周会被记成空——往后翻再回来时必须允许重录，
    // 否则这一周会在最终合并里永远缺课。
    var w = weekNo();
    if (!w || (byWeek[w] && byWeek[w].length)) { return w; }
    var cells = grab();
    if (cells && cells.length > 0) {
      byWeek[w] = cells;
      // 空周重录后 w 可能已经在 weekList 里，去重避免最终结果里同一周出现两次
      if (weekList.indexOf(w) < 0) { weekList.push(w); }
      report('progress:' + w);
    } else {
      byWeek[w] = [];
      if (weekList.indexOf(w) < 0) { weekList.push(w); }
    }
    return w;
  }
  function step(dir, done) {
    if (!active()) { return; }
    guard++;
    if (guard > 120) { done(); return; }
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
        grabStable(function () {
          if (!active()) { return; }
          record();
          setTimeout(function () { if (active()) { step(dir, done); } }, 50);
        });
      } else if (tries > 50) {
        clearInterval(timer);
        done();
      }
    }, 50);
  }
  // 翻页老路：先往后翻到学期末、再往回翻到第 1 周。下拉框不可用时的兜底路径。
  function legacyScan(done) {
    step('>', function () {
      if (!active()) { return; }
      step('<', done);
    });
  }
  function finishAll() {
    if (wrapDone || !active()) { return; }
    wrapDone = true;
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
    for (var i2 = 0; i2 < order.length; i2++) { list.push(courses[order[i2]]); }
    var m = metrics();
    report('done:' + JSON.stringify({
      weeks: weekList.sort(function (a, b) { return a - b; }),
      courses: list,
      body: body0,
      unit: m.unit,
      perRow: m.perRow,
      picker: picker ? 'jump' : 'step'
    }));
  }
  var wrapDone = false;
  // 周次直达：下拉框里的周次选项可以直接点过去，不必一周一周「>/」翻。
  // 先探测页面上的选项（原生 select 的 option 优先，其次可点选的文字元素），
  // 只有把 1..W 连续列全的列表才可信；跳完列表再往后探一页防列表过期。
  // 任何一次跳转都要验证「周次已变成目标且网格稳定」，连续 3 次失败就整段退回翻页老路。
  var picker = null;
  var jumpFails = 0;
  function weekNumFromText(t) {
    var m = /^第\s*([0-9]{1,2})\s*周(?:\s|$)/.exec((t || '').trim());
    return m ? parseInt(m[1], 10) : 0;
  }
  function findWeekOptions() {
    var found = {};
    var selects = document.querySelectorAll('select');
    for (var s = 0; s < selects.length; s++) {
      var opts = selects[s].querySelectorAll('option');
      for (var i = 0; i < opts.length; i++) {
        var w = weekNumFromText(txt(opts[i]));
        if (w >= 1 && w <= 30) {
          found[w] = { mode: 'select', select: selects[s], value: opts[i].value, week: w };
        }
      }
    }
    if (Object.keys(found).length) { return found; }
    var all = document.querySelectorAll('li,span,div,a,button');
    for (var j = 0; j < all.length; j++) {
      var e = all[j];
      if (e.children && e.children.length > 1) { continue; }
      var w2 = weekNumFromText(txt(e));
      if (w2 >= 1 && w2 <= 30 && !found[w2]) {
        found[w2] = { mode: 'click', el: e, week: w2 };
      }
    }
    return found;
  }
  function pickerTrusted(found) {
    var weeks = Object.keys(found).map(Number).sort(function (a, b) { return a - b; });
    if (weeks.length < 8 || weeks[0] !== 1) { return false; }
    for (var i = 0; i < weeks.length; i++) {
      if (weeks[i] !== i + 1) { return false; }
    }
    return true;
  }
  function jumpTo(w, cb) {
    if (!picker) { cb(false); return; }
    var opt = picker.byWeek[w];
    if (!opt) { cb(false); return; }
    try {
      if (opt.mode === 'select') {
        opt.select.value = opt.value;
        opt.select.dispatchEvent(new Event('change', { bubbles: true }));
      } else {
        fire(opt.el);
      }
    } catch (e) { cb(false); return; }
    waitStableWeek(w, cb);
  }
  // 直达失败时的单步翻页：朝目标方向点一次「>/」，
  // 周次变了且网格稳定后录入，再由调用方决定下一步。
  function stepTowards(w, done) {
    if (!active()) { return; }
    var dir = w > weekNo() ? '>' : '<';
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
        grabStable(function () {
          if (!active()) { return; }
          record();
          done();
        });
      } else if (tries > 50) { clearInterval(timer); done(); }
    }, 50);
  }
  function visitList(weeks, done) {
    if (!active()) { return; }
    if (picker === null) { legacyScan(done); return; }
    if (!weeks.length) { done(); return; }
    var w = weeks[0];
    var rest = weeks.slice(1);
    // 已经拿到非空数据的周直接跳过（稳定网格的录入不重复花时间）
    if (byWeek[w] && byWeek[w].length) { visitList(rest, done); return; }
    var cur = weekNo();
    if (cur === w) {
      grabStable(function () {
        if (!active()) { return; }
        record();
        visitList(rest, done);
      });
      return;
    }
    guard++;
    if (guard > 120) { done(); return; }
    jumpTo(w, function (ok) {
      if (!active()) { return; }
      if (ok) {
        record();
        visitList(rest, done);
      } else {
        jumpFails++;
        if (jumpFails >= 3) {
          // 连续失败说明对下拉框结构的猜测不对，整段退回翻页老路
          picker = null;
          legacyScan(done);
          return;
        }
        stepTowards(w, function () { visitList(rest, done); });
      }
    });
  }
  // 下拉框可能过期或不全：扫完列表后往后翻一页，看看有没有列表之外的周。
  // 真有就继续往后扫到尽头；往后翻不动（或环绕回前面的周）就正常收尾。
  function probeBeyond(done) {
    if (!active()) { return; }
    var btn = findNav('>');
    if (!btn) { done(); return; }
    guard++;
    if (guard > 120) { done(); return; }
    var before = weekNo();
    fire(btn);
    var tries = 0;
    var timer = setInterval(function () {
      if (!active()) { clearInterval(timer); return; }
      tries++;
      var now = weekNo();
      if (now && now > before) {
        clearInterval(timer);
        grabStable(function () {
          if (!active()) { return; }
          record();
          step('>', done);
        });
      } else if (tries > 20) { clearInterval(timer); done(); }
    }, 50);
  }
  gxkReady(function () {
    if (!active()) { return; }
    grabStable(function () {
      if (!active()) { return; }
      record();
      var found = findWeekOptions();
      if (pickerTrusted(found)) {
        picker = { byWeek: found };
        var weeks = Object.keys(found).map(Number).sort(function (a, b) { return a - b; });
        visitList(weeks, function () {
          if (!active()) { return; }
          probeBeyond(finishAll);
        });
      } else {
        legacyScan(finishAll);
      }
    });
  });
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
    // 页面「正在显示的周」：从「>/」按钮所在的表头小块结构提取 ——
    // 正文里的下拉框列着全学期周次，按文字顺序取会把格子贴错周。
    // 结构提取失败时回 0，由 App 侧退回正文识别（那时页面还没翻过周，识别的也是真实当前周）。
    function displayedWeek() {
      var all = document.querySelectorAll('div,span,a,i,button');
      for (var n = 0; n < all.length; n++) {
        var el = all[n];
        if (el.children && el.children.length > 0) { continue; }
        var tt = txt(el);
        if (tt !== '>' && tt !== '<') { continue; }
        var p = el.parentElement;
        for (var d = 0; d < 4 && p; d++) {
          var mw = /第\s*([0-9]{1,2})\s*周/.exec(p.innerText || '');
          if (mw) {
            var wv = parseInt(mw[1], 10);
            if (wv >= 1 && wv <= 30) { return wv; }
          }
          p = p.parentElement;
        }
      }
      var mb = /<\s*第\s*([0-9]{1,2})\s*周/.exec(document.body ? (document.body.innerText || '') : '');
      return mb ? parseInt(mb[1], 10) : 0;
    }
    report('scrape', JSON.stringify({
      href: location.href,
      bodyText: bodyText.slice(0, 6000),
      grid: grid,
      displayedWeek: displayedWeek()
    }));
  };
  // 就绪等待：网格没渲染出来就抓等于白抓。一出现立刻抓，5 秒兜底照常执行。
  function gxkReady(fn) {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (document.querySelector('.Courses-content') || document.querySelector('ul.stage_k')) {
        clearInterval(timer); fn();
      } else if (tries >= 100) { clearInterval(timer); fn(); }
    }, 50);
  }
  gxkReady(function () { window.__gxkScrape(); });
})();
""".trimIndent()

    /**
     * 轻量周次探测：只回传当前显示周数字符串，给 App 侧「逐周驱动」轮询翻页是否
     * 成功用。每 100ms 跑一次，必须便宜 —— 只读一次正文，不遍历 DOM。
     * 优先认「<」箭头紧邻的「第N周」（真实页面的表头就夹在 < > 之间），
     * 箭头锚定不到才退回第一个带日期的行、最后宽松匹配。
     * 探测结果只用于「翻页有没有成功」的变化检测；正式的周次标签由
     * scrapeScript 的结构化 displayedWeek 字段给出。
     */
    val weekProbeScript: String = """
(function () {
  function weekNo() {
    var body = document.body ? (document.body.innerText || '') : '';
    var ma = /<\s*第\s*([0-9]{1,2})\s*周/.exec(body) || /第\s*([0-9]{1,2})\s*周\s*\n?\s*>/.exec(body);
    if (ma) {
      var wa = parseInt(ma[1], 10);
      if (wa >= 1 && wa <= 30) { return String(wa); }
    }
    var re = /第\s*([0-9]{1,2})\s*周\s*([0-9]{4})-([0-9]{2})-([0-9]{2})/g;
    var m;
    while ((m = re.exec(body))) {
      var w = parseInt(m[1], 10);
      if (w >= 1 && w <= 30) { return String(w); }
    }
    var re2 = /第\s*([0-9]{1,2})\s*周/g;
    while ((m = re2.exec(body))) {
      var w2 = parseInt(m[1], 10);
      if (w2 >= 1 && w2 <= 30) { return String(w2); }
    }
    return '0';
  }
  return weekNo();
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
