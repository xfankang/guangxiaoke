package com.jianke.schedule

import com.jianke.schedule.web.SchoolEndpoints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolEndpointsTest {

    @Test
    fun acceptsOnlyExpectedSchoolHosts() {
        assertTrue(SchoolEndpoints.isTrustedUrl(SchoolEndpoints.SSO_LOGIN))
        assertTrue(SchoolEndpoints.isTrustedUrl(SchoolEndpoints.COURSE_TABLE))
        assertFalse(SchoolEndpoints.isTrustedUrl("https://jw.gzist.edu.cn.example.com/jwglxt/"))
        assertFalse(SchoolEndpoints.isTrustedUrl("https://jw.gzist.edu.cn@example.com/jwglxt/"))
        assertFalse(SchoolEndpoints.isTrustedUrl("file:///android_asset/jw.gzist.edu.cn"))
        assertFalse(SchoolEndpoints.isTrustedUrl(null))
    }

    @Test
    fun bridgeAcceptsCourseSystemButNotLoginHost() {
        assertTrue(SchoolEndpoints.isCourseSystemUrl(SchoolEndpoints.COURSE_TABLE))
        assertFalse(SchoolEndpoints.isCourseSystemUrl(SchoolEndpoints.SSO_LOGIN))
    }
}
