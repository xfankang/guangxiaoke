package com.jianke.schedule.web

/**
 * 直连接口的「探测 + 自检」脚本。
 *
 * 之前那次失败的原因没查清就上线了，这次不再「拿到 kbList 就当成功」，而是：
 *   1. 先把页面上**真实渲染出来的**这一周课表抓下来（唯一可信的基准）
 *   2. 把几个候选接口全部请求一遍（并发，不再串行等每个接口）
 *   3. 用页面数据给每个返回**打分**：接口里能对上多少门课
 *   4. 同时试两种节次编码（小节 1-10 / 大节 1-5），看哪种能对上
 * 只有完全对得上（100%）才用接口；对不上就把诊断信息回传，由 App 回退到页面解析。
 *
 * 收集课程数组时与 Kotlin 侧 ZfsoftParser 同口径：kbList 之外还有 sjkList 等
 * 课程数组 —— 之前只认 kbList，接口回别的数组名就被白白判死、永远走慢路。
 */
object ApiProbeJs {

    val script: String = """
(function () {
  if (window.__gxkApiRunning) { return; }
  window.__gxkApiRunning = true;
  function report(payload) {
    try { window.GXKBridge.post('api', String(payload)); } catch (e) { }
  }
  // 心跳：告诉 App「脚本还活着、别掐断」。页面停在没有课的一周时，就绪等待 +
  // 周次对齐 + 6 个接口请求可能超过 20 秒，旧实现会被 App 的看门狗提前掐断，
  // 结果回传时 phase 已经推进、被直接丢弃 —— 表现就是「接口从没失败过，但也从没成功过」。
  function hb(stage) {
    report(JSON.stringify({ hb: stage }));
  }
  function txt(el) {
    if (!el) { return ''; }
    var t = el.innerText || el.textContent || '';
    return t.replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').trim();
  }
  function norm(s) {
    return String(s || '')
      .replace(/[\s\(\)（）\[\]【】·、,，.。\-—_\/\\]/g, '')
      .toUpperCase();
  }
  function field(id) {
    var el = document.getElementById(id);
    return el ? String(el.value || '') : '';
  }
  // 等「课表网格容器」出现就往下走（6 秒兜底）。注意不能等「有课程格子」——
  // 页面停在没有课的那一周时（例如上次逐周扫描把它留在第 1 周）本来就没有格子，
  // 白等 15 秒之后仍然拿不到标尺，校验必然失败。真正的对策是下面的周次对齐。
  function gxkReady(fn) {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (document.querySelector('.Courses-content') || document.querySelector('ul.stage_k')) {
        clearInterval(timer); fn();
      } else if (tries >= 120) { clearInterval(timer); fn(); }
    }, 50);
  }

  // ---- 周次对齐 ----
  // 页面可能停在没有课的一周（典型：上一次逐周扫描把状态留在第 1 周，会话记住了）。
  // 那样页面上一个格子都没有、没法给接口当比对标尺，校验必然失败、白退慢路径。
  // 动手前先按正文里的「第N周 起/止日期」算出今天属于第几周，把页面直接跳到那一周。
  function todayNum() {
    var d = new Date();
    return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
  }
  function weekFromBodyDates(body) {
    var re = /第\s*([0-9]{1,2})\s*周\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})(?:\s*\/\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}))?/g;
    var m, first = 0;
    var today = todayNum();
    while ((m = re.exec(body))) {
      var w = parseInt(m[1], 10);
      if (!(w >= 1 && w <= 30)) { continue; }
      if (!first) { first = w; }
      var d1 = parseInt(m[2], 10) * 10000 + parseInt(m[3], 10) * 100 + parseInt(m[4], 10);
      var d2 = m[5] ? (parseInt(m[5], 10) * 10000 + parseInt(m[6], 10) * 100 + parseInt(m[7], 10)) : d1;
      if (today >= d1 && today <= d2) { return w; }
    }
    return first;
  }
  function shownWeek() {
    var w = parseInt(field('zs_hide'), 10);
    if (w >= 1 && w <= 30) { return w; }
    var m = /第\s*([0-9]{1,2})\s*周/.exec(document.body ? (document.body.innerText || '') : '');
    var w2 = m ? parseInt(m[1], 10) : 0;
    return (w2 >= 1 && w2 <= 30) ? w2 : 0;
  }
  function weekOptions() {
    var found = {};
    var selects = document.querySelectorAll('select');
    for (var s = 0; s < selects.length; s++) {
      var opts = selects[s].querySelectorAll('option');
      for (var i = 0; i < opts.length; i++) {
        var m = /^第\s*([0-9]{1,2})\s*周(?:\s|$)/.exec(txt(opts[i]));
        var w = m ? parseInt(m[1], 10) : 0;
        if (w >= 1 && w <= 30) { found[w] = { mode: 'select', select: selects[s], value: opts[i].value }; }
      }
    }
    if (Object.keys(found).length) { return found; }
    var all = document.querySelectorAll('li,span,div,a,button');
    for (var j = 0; j < all.length; j++) {
      var e = all[j];
      if (e.children && e.children.length > 1) { continue; }
      var m2 = /^第\s*([0-9]{1,2})\s*周(?:\s|$)/.exec(txt(e));
      var w2 = m2 ? parseInt(m2[1], 10) : 0;
      if (w2 >= 1 && w2 <= 30 && !found[w2]) { found[w2] = { mode: 'click', el: e }; }
    }
    return found;
  }
  function waitWeek(w, cb) {
    var tries = 0;
    var t = setInterval(function () {
      tries++;
      if (shownWeek() === w) { clearInterval(t); cb(true); }
      else if (tries >= 120) { clearInterval(t); cb(false); }
    }, 50);
  }
  function alignWeek(fn) {
    var body = document.body ? (document.body.innerText || '') : '';
    var target = weekFromBodyDates(body);
    var cur = shownWeek();
    if (!target || cur === target) { fn(); return; }
    var opts = weekOptions();
    var opt = opts[target];
    if (!opt) { fn(); return; }
    try {
      if (opt.mode === 'select') {
        opt.select.value = opt.value;
        opt.select.dispatchEvent(new Event('change', { bubbles: true }));
      } else {
        opt.el.click();
      }
    } catch (e) { fn(); return; }
    waitWeek(target, function (ok) {
      if (!ok) { fn(); return; }
      var tries = 0;
      var t = setInterval(function () {
        tries++;
        if (document.querySelector('.Courses-content .course-hasContent')) { clearInterval(t); fn(); }
        else if (tries >= 120) { clearInterval(t); fn(); }
      }, 50);
    });
  }

  // 与 Kotlin 侧 ZfsoftParser.looksLikeCourseList 同口径：名字 + 星期 + 节次齐了才算课表记录
  function looksLikeCourseList(arr) {
    for (var i = 0; i < Math.min(arr.length, 5); i++) {
      var o = arr[i];
      if (!o || typeof o !== 'object') { continue; }
      var hasName = String(o.kcmc || o.courseName || '').trim();
      var hasDay = o.xqj !== undefined || o.xingqi !== undefined;
      var hasPeriod = o.jcs !== undefined || o.jc !== undefined || o.periods !== undefined;
      if (hasName && hasDay && hasPeriod) { return true; }
    }
    return false;
  }
  function courseArrays(data) {
    var out = [];
    var keys = Object.keys(data || {});
    for (var i = 0; i < keys.length; i++) {
      var arr = data[keys[i]];
      if (!arr || typeof arr.length !== 'number' || !arr.length) { continue; }
      if (looksLikeCourseList(arr)) { out.push({ name: keys[i], list: arr }); }
    }
    return out;
  }

  function main() {
    var pageCells = [];
    var ulCount = 0;
    var onePerRow = 0;
    var twoPerRow = 0;
    var content = document.querySelector('.Courses-content');
    if (content) {
      var uls = content.children;
      for (var i = 0; i < uls.length; i++) {
        if (String(uls[i].tagName).toUpperCase() !== 'UL') { continue; }
        ulCount++;
        var lis = uls[i].children;
        for (var j = 0; j < lis.length; j++) {
          var span = lis[j].querySelector ? lis[j].querySelector('.course-hasContent') : null;
          if (!span) { continue; }
          var t = txt(span);
          if (!t) { continue; }
          var st = span.getAttribute('style') || '';
          var hm = /height:\s*([0-9.]+)px/.exec(st);
          var cm = /background-color:\s*(rgb\([^)]*\))/.exec(st);
          pageCells.push({
            t: t,
            d: j,
            p: i,
            q: span.getAttribute('data-qsj') || '',
            h: hm ? parseFloat(hm[1]) : 0,
            col: cm ? cm[1] : ''
          });
          var qv = parseInt(span.getAttribute('data-qsj'), 10);
          if (qv > 0 && i > 0) {
            var ratio = (qv - 1) / i;
            if (Math.abs(ratio - 2) < 0.25) { twoPerRow++; } else if (Math.abs(ratio - 1) < 0.25) { onePerRow++; }
          }
        }
      }
    }

    var body = (document.body ? (document.body.innerText || '') : '').slice(0, 3000);
    var xnm = field('xnm_hide');
    var xqm = field('xqm_hide');
    var contentH = content ? (content.offsetHeight || 0) : 0;
    var meta = {
      xnm: xnm,
      xqm: xqm,
      body: body,
      pageCount: pageCells.length,
      unit: (contentH > 0 && ulCount > 0) ? contentH / ulCount : 0,
      perRow: (onePerRow + twoPerRow === 0) ? ((ulCount > 0 && ulCount <= 6) ? 2 : 1) : (twoPerRow > onePerRow ? 2 : 1),
      displayedWeek: shownWeek()
    };

    function scoreOf(apiText) {
      var data;
      try { data = JSON.parse(apiText); } catch (e) { return null; }
      var arrays = courseArrays(data);
      if (!arrays.length) { return null; }
      var combined = [];
      for (var i = 0; i < arrays.length; i++) {
        var list = arrays[i].list;
        for (var j = 0; j < list.length; j++) { combined.push(list[j]); }
      }
      function build(bigMode) {
        var keys = {};
        for (var i = 0; i < combined.length; i++) {
          var o = combined[i];
          var name = norm(o.kcmc || o.courseName || o.kcname || o.kcmcBz || '');
          var dayRaw = o.xqj !== undefined ? o.xqj : (o.xingqi !== undefined ? o.xingqi : (o.dayOfWeek !== undefined ? o.dayOfWeek : o.xq));
          var day = parseInt(dayRaw, 10);
          var jcsRaw = o.jcs !== undefined ? o.jcs : (o.jcsText !== undefined ? o.jcsText : (o.jc !== undefined ? o.jc : (o.periods !== undefined ? o.periods : '')));
          var jcs = String(jcsRaw || '');
          var nums = jcs.match(/[0-9]+/g) || [];
          if (!name || !(day >= 1 && day <= 7) || !nums.length) { continue; }
          var start = parseInt(nums[0], 10);
          if (bigMode) { start = start * 2 - 1; }
          keys[name + '|' + day + '|' + start] = true;
        }
        return keys;
      }
      function hitRate(keys) {
        if (!pageCells.length) { return 0; }
        var hit = 0;
        for (var k = 0; k < pageCells.length; k++) {
          var c = pageCells[k];
          var nm = norm((c.t.split('\n')[0] || ''));
          var d = c.d + 1;
          // 缺 data-qsj 时和 Kotlin 侧 parsePageCells 用同一个兜底：按每行小节数推起始节
          var p = parseInt(c.q, 10);
          if (!p || p < 1) { p = c.p * meta.perRow + 1; }
          if (keys[nm + '|' + d + '|' + p]) { hit++; }
        }
        return hit / pageCells.length;
      }
      var names = [];
      for (var a = 0; a < arrays.length; a++) { names.push(arrays[a].name); }
      return { small: hitRate(build(false)), big: hitRate(build(true)), count: combined.length, arrays: names.join(',') };
    }

    var params = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm);
    var targets = [
      '/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N253508',
      '/jwglxt/kbcx/xskbcx_cxXsKb.html',
      '/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=Y253511'
    ];
    var attempts = [];
    for (var a = 0; a < targets.length; a++) {
      (function (u) {
        attempts.push({ url: u, method: 'POST', run: function () {
          return fetch(u, { method: 'POST', credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params
          }).then(function (r) { return r.text(); });
        } });
        var joined = u + (u.indexOf('?') >= 0 ? '&' : '?') + params;
        attempts.push({ url: joined, method: 'GET', run: function () {
          return fetch(joined, { method: 'GET', credentials: 'include' })
            .then(function (r) { return r.text(); });
        } });
      })(targets[a]);
    }

    // 全部尝试并发发出：串行等 6 个接口要 1.5~2.5s，并发只等最慢的那个
    hb('requesting:' + pageCells.length + 'cells');
    var results = [];
    var settled = 0;
    function finishProbe() {
      results.sort(function (x, y) { return y.best - x.best; });
      var top = results[0] || null;
      report(JSON.stringify({
        ok: !!(top && top.best >= 0.999),
        meta: meta,
        page: pageCells.slice(0, 60),
        scores: results.map(function (r) {
          return { url: r.url, method: r.method, small: r.small, big: r.big, count: r.count, arrays: r.arrays };
        }),
        text: top ? top.text.slice(0, 400000) : '',
        bigMode: !!(top && top.big > top.small)
      }));
    }
    for (var b = 0; b < attempts.length; b++) {
      (function (at) {
        at.run().then(function (text) {
          if (text) {
            var sc = scoreOf(text);
            if (sc) {
              results.push({ url: at.url, method: at.method, text: text,
                small: sc.small, big: sc.big, count: sc.count, arrays: sc.arrays,
                best: Math.max(sc.small, sc.big) });
            }
          }
          settled++;
          if (settled === attempts.length) { finishProbe(); }
        }).catch(function () {
          settled++;
          if (settled === attempts.length) { finishProbe(); }
        });
      })(attempts[b]);
    }
  }

  // 周次对齐会异步等页面刷新，对齐完再抓格子；对齐失败也照常继续（校验失败会正常回退）
  hb('injected');
  gxkReady(function () {
    hb('grid-ready');
    alignWeek(function () {
      hb('week-aligned:' + shownWeek());
      main();
    });
  });
})();
""".trimIndent()
}
