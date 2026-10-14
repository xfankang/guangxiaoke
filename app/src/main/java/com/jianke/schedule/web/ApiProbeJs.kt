package com.jianke.schedule.web

/**
 * 直连接口的「探测 + 自检」脚本。
 *
 * 之前那次失败的原因没查清就上线了，这次不再「拿到 kbList 就当成功」，而是：
 *   1. 先把页面上**真实渲染出来的**这一周课表抓下来（唯一可信的基准）
 *   2. 把几个候选接口全部请求一遍
 *   3. 用页面数据给每个返回**打分**：接口里能对上多少门课
 *   4. 同时试两种节次编码（小节 1-10 / 大节 1-5），看哪种能对上
 * 只有完全对得上（100%）才用接口；对不上就把诊断信息回传，由 App 回退到页面解析。
 */
object ApiProbeJs {

    const val BRIDGE_NAME = ScheduleJs.BRIDGE_NAME

    val script: String = """
(function () {
  if (window.__gxkApiRunning) { return; }
  window.__gxkApiRunning = true;
  function report(payload) {
    try { window.GXKBridge.post('api', String(payload)); } catch (e) { }
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

  var pageCells = [];
  var content = document.querySelector('.Courses-content');
  if (content) {
    var uls = content.children;
    for (var i = 0; i < uls.length; i++) {
      if (String(uls[i].tagName).toUpperCase() !== 'UL') { continue; }
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
      }
    }
  }

  var body = (document.body ? (document.body.innerText || '') : '').slice(0, 3000);
  var xnm = field('xnm_hide');
  var xqm = field('xqm_hide');
  var meta = { xnm: xnm, xqm: xqm, body: body, pageCount: pageCells.length };

  function scoreOf(apiText) {
    var data;
    try { data = JSON.parse(apiText); } catch (e) { return null; }
    var list = data && data.kbList;
    if (!list || !list.length) { return null; }
    function build(bigMode) {
      var keys = {};
      for (var i = 0; i < list.length; i++) {
        var o = list[i];
        var name = norm(o.kcmc || o.courseName || '');
        var day = parseInt(o.xqj, 10);
        var jcs = String(o.jcs || o.jc || '');
        var nums = jcs.match(/[0-9]+/g) || [];
        if (!name || !day || !nums.length) { continue; }
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
        var p = parseInt(c.q, 10) || (c.p + 1);
        if (keys[nm + '|' + d + '|' + p]) { hit++; }
      }
      return hit / pageCells.length;
    }
    return { small: hitRate(build(false)), big: hitRate(build(true)), count: list.length };
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

  var results = [];
  function next(i) {
    if (i >= attempts.length) {
      results.sort(function (x, y) { return y.best - x.best; });
      var top = results[0] || null;
      report(JSON.stringify({
        ok: !!(top && top.best >= 0.999),
        meta: meta,
        page: pageCells.slice(0, 60),
        scores: results.map(function (r) {
          return { url: r.url, method: r.method, small: r.small, big: r.big, count: r.count };
        }),
        text: top ? top.text.slice(0, 400000) : '',
        bigMode: !!(top && top.big > top.small)
      }));
      return;
    }
    var at = attempts[i];
    at.run().then(function (text) {
      if (text && text.indexOf('kbList') >= 0) {
        var sc = scoreOf(text);
        if (sc) {
          results.push({ url: at.url, method: at.method, text: text,
            small: sc.small, big: sc.big, count: sc.count,
            best: Math.max(sc.small, sc.big) });
        }
      }
      next(i + 1);
    }).catch(function () { next(i + 1); });
  }
  next(0);
})();
""".trimIndent()
}