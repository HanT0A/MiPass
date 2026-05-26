package com.hanzg.mipass.utils

enum class AuthState { OOBE, UNLOCK, BIOMETRIC, DONE }

enum class ThemeMode(val value: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark")
}

object UiText {
    const val CATEGORY_ALL = "全部"
    const val VAULT_TITLE = "密码库"
    const val SETTINGS_TITLE = "设置"
    const val ADD_TITLE = "新增"
    const val DELETE_CONFIRM_TITLE = "确认删除"
    const val DELETE_CONFIRM_MSG = "删除后无法恢复，确定要删除这条记录吗？"
    const val NO_PASSWORD = "暂无密码"
    const val NO_PASSWORD_HINT = "点击 + 新增密码"
}
