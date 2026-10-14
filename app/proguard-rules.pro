# WebView 通过反射调用 @JavascriptInterface 标注的方法，不能被混淆掉
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.jianke.schedule.web.** { *; }

# 彩蛋/壁纸用到的解析不涉及反射，其余交给默认规则
