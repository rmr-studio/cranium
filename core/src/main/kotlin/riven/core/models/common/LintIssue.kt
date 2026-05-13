package cranium.core.models.common

import cranium.core.enums.common.validation.IssueLevel

data class LintIssue(
    val path: String,
    val level: IssueLevel,
    val message: String,
)