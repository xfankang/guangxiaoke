package com.jianke.schedule.web

/**
 * 广州理工学院的两个入口：
 *
 * 1. 统一身份认证（联奕 CAS，学生平时用的就是这套账号密码）
 * 2. 正方教务系统本地登录（账号是学号，但密码是「证件号码后六位」这种初始密码，
 *    跟统一身份认证不是一回事，用统一身份认证的密码登会提示「用户名或密码不正确」）
 *
 * 所以默认走统一身份认证。
 */
object SchoolEndpoints {

    /** 统一身份认证入口（登录成功后 CAS 会带 ticket 跳回正方）。 */
    const val SSO_LOGIN =
        "https://ids.gzist.edu.cn/lyuapServer/login?service=http://jw.gzist.edu.cn/sso/lyiotlogin"

    /** 正方教务系统本地登录页。 */
    const val LOCAL_LOGIN = "http://jw.gzist.edu.cn/jwglxt/xtgl/login_slogin.html"

    const val JW_HOST = "jw.gzist.edu.cn"
    private const val IDS_HOST = "ids.gzist.edu.cn"

    const val COURSE_TABLE =
        "http://jw.gzist.edu.cn/jwglxt/kbcx/xskbcxMobile_cxTimeTableIndex.html?gnmkdm=Y253511&hasHeader=true"

    /** 登录和课表只允许在这两个明确的主机名内跳转。 */
    fun isTrustedUrl(url: String?): Boolean = hasHost(url, IDS_HOST) || hasHost(url, JW_HOST)

    /** 只有教务页面可以调用导入桥接，登录页和其他来源一律忽略。 */
    fun isCourseSystemUrl(url: String?): Boolean = hasHost(url, JW_HOST)

    private fun hasHost(url: String?, expectedHost: String): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = java.net.URI(url)
            (uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)) &&
                uri.host.equals(expectedHost, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
