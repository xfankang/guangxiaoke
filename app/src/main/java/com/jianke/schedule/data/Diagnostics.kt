package com.jianke.schedule.data

import org.json.JSONArray
import org.json.JSONObject

/** 把抓到的页面结构压成一段人能看懂的短文本，方便截图/复制发给开发者。 */
object Diagnostics {

    fun summarize(raw: String): String {
        val sb = StringBuilder()
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return raw.take(2000)
        }
        sb.append("URL: ").append(root.optString("href")).append('\n')
        sb.append("BODY: ").append(root.optString("bodyText").replace("\n", " | ").take(300)).append('\n')

        val grid = root.optJSONObject("grid")
        if (grid == null) {
            sb.append("GRID: null\n")
        } else {
            sb.append("HEAD: ").append(join(grid.optJSONArray("head"))).append('\n')
            sb.append("DATES: ").append(join(grid.optJSONArray("dates"))).append('\n')
            val hidden = grid.optJSONObject("hidden")
            sb.append("HIDDEN: ").append(hidden?.toString() ?: "-").append('\n')
            sb.append("CONTENT_H: ").append(grid.optInt("contentH")).append('\n')
            val stages = grid.optJSONArray("stages")
            sb.append("STAGES: ").append(stages?.length() ?: 0).append('\n')
            if (stages != null) {
                for (i in 0 until minOf(stages.length(), 3)) {
                    val st = stages.optJSONObject(i) ?: continue
                    sb.append("  ").append(st.optString("c")).append(": ")
                    val cells = st.optJSONArray("cells") ?: continue
                    for (j in 0 until minOf(cells.length(), 7)) {
                        val c = cells.optJSONObject(j) ?: continue
                        sb.append('[').append(c.optInt("h")).append(']')
                            .append(c.optString("t").replace("\n", "/").take(20)).append(" | ")
                    }
                    sb.append('\n')
                }
                val last = stages.optJSONObject(stages.length() - 1)
                sb.append("  LAST(").append(last?.optString("c")).append("): ")
                val lc = last?.optJSONArray("cells")
                if (lc != null) {
                    for (j in 0 until minOf(lc.length(), 7)) {
                        val c = lc.optJSONObject(j) ?: continue
                        sb.append('[').append(c.optInt("h")).append(']')
                            .append(c.optString("t").replace("\n", "/").take(16)).append(" | ")
                    }
                }
                sb.append('\n')
                val first = stages.optJSONObject(0)?.optJSONArray("cells")?.optJSONObject(0)
                if (first != null) {
                    sb.append("CELL_HTML: ").append(first.optString("x").replace("\n", " ").take(400)).append('\n')
                }
            }
        }

        val navs = root.optJSONArray("navs")
        sb.append("NAVS: ").append(navs?.length() ?: 0).append('\n')
        if (navs != null) {
            for (i in 0 until minOf(navs.length(), 8)) {
                val o = navs.optJSONObject(i) ?: continue
                sb.append("  ").append(o.optString("tag")).append('.').append(o.optString("c"))
                    .append(" id=").append(o.optString("i"))
                    .append(" t=").append(o.optString("t"))
                    .append(" onclick=").append(o.optString("o").take(120)).append('\n')
            }
        }

        val scripts = root.optJSONArray("scripts")
        sb.append("SCRIPTS: ").append(scripts?.length() ?: 0).append('\n')
        if (scripts != null && scripts.length() > 0) {
            sb.append(scripts.optString(0).replace("\n", " ").take(1200)).append('\n')
        }
        return sb.toString()
    }

    private fun join(arr: JSONArray?): String {
        if (arr == null) return "-"
        val sb = StringBuilder()
        for (i in 0 until minOf(arr.length(), 10)) {
            sb.append(arr.optString(i)).append(' ')
        }
        return sb.toString()
    }
}
