package com.nam.novelreader.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * BackgroundWebView — Một WebView tùy chỉnh được thiết kế để chạy trong nền/chạy ngầm (Background Service).
 * Nó ghi đè các sự kiện thay đổi khả năng hiển thị và tiêu điểm của Window để đánh lừa Android
 * rằng WebView này luôn luôn hiển thị (VISIBLE) và có tiêu điểm (focused).
 * Điều này ngăn chặn việc Android tạm ngưng (suspend) hoặc giảm hiệu năng (throttle) thực thi JavaScript
 * của WebView khi ứng dụng chạy ngầm trên Android 13, 14+.
 */
class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Luôn báo cáo VISIBLE để tránh bị đóng băng JavaScript khi chạy ngầm
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        // Luôn báo cáo VISIBLE để tránh bị đóng băng JavaScript khi chạy ngầm
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun hasWindowFocus(): Boolean {
        // Luôn báo cáo có focus để JS chạy bình thường
        return true
    }
}
