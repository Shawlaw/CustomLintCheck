package io.github.shawlaw.liblintcheck

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.*

class IllegalCheckRegistry: IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(IllegalCheckClassScanner.ISSUE)

    override val api: Int
        get() = CURRENT_API
}