package com.playtoolbox.app

data class ParsedOutput(val title: String, val body: String, val isError: Boolean, val raw: String)

object OutputParser {
    fun parse(command: String, raw: String, exitOk: Boolean): ParsedOutput {
        if (!exitOk) return ParsedOutput("❌ 执行失败", translateError(raw), true, raw)
        val c = command.trim(); val r = raw.trim()
        if (r.isBlank() || r == "(无输出)") return ParsedOutput("✅ 执行成功", "命令已执行，无输出内容。", false, raw)

        val translatedValue = when(r) {
            "1" -> "开启 / 已启用"
            "0" -> "关闭 / 已禁用"
            "true" -> "是 / 开启"
            "false" -> "否 / 关闭"
            else -> r
        }

        return when {
            c.startsWith("settings get") -> ParsedOutput("⚙️ 设置读取成功", "当前状态为：$translatedValue", false, raw)
            c.startsWith("settings put") -> ParsedOutput("✅ 设置修改成功", "设置已成功写入系统。", false, raw)
            c.contains("getprop ro.build.type") -> ParsedOutput("⚙️ 系统编译类型", "当前类型：$translatedValue", false, raw)
            c.startsWith("cat ") && c.contains("capacity") -> ParsedOutput("🔋 电池电量", "当前电量：$r %", false, raw)
            c.startsWith("cat ") && c.contains("temp") -> ParsedOutput("🌡️ 温度", "当前温度：${r.toIntOrNull()?.div(1000.0) ?: r} ℃", false, raw)
            c.startsWith("cat ") && c.contains("freq") -> ParsedOutput("⚡ 频率", "当前频率：${r.toLongOrNull()?.div(1000000.0) ?: r} GHz", false, raw)
            c.startsWith("pm disable-user") -> ParsedOutput("🧊 冻结成功", "应用已被禁用，无法在后台运行。", false, raw)
            c.startsWith("pm enable") -> ParsedOutput("✅ 解冻成功", "应用已恢复正常使用。", false, raw)
            c.startsWith("pm clear") -> ParsedOutput("🗑️ 数据清空", "应用数据已全部清除。", false, raw)
            c.startsWith("am force-stop") -> ParsedOutput("⛔ 已强停", "应用进程已结束。", false, raw)
            c.startsWith("pm uninstall") -> ParsedOutput("🗑️ 卸载成功", "应用已被卸载。", false, raw)
            c.contains("WifiConfigStore") -> ParsedOutput("🔑 WiFi 密码", "已获取配置文件，请在下方查找 SSID 和 Password：\n\n$r", false, raw)
            else -> ParsedOutput("✅ 执行结果", r, false, raw)
        }
    }

    private fun translateError(e: String): String {
        val lower = e.lowercase()
        return when {
            lower.contains("securityexception") || lower.contains("does not have access") || lower.contains("permission denial") || lower.contains("permission denied") ->
                "❌ 系统拒绝执行：该操作需要 Root 权限，或当前安卓版本限制了底层接口。\n\n请确保：\n1. 已授予本应用 Root 权限\n2. 如果已 Root，请检查 Magisk 是否正常工作。"
            lower.contains("su: not found") -> "❌ 未找到 su 命令：您的设备似乎没有 Root。"
            lower.contains("no such file") -> "❌ 文件或节点不存在：您的手机系统可能不支持此功能。"
            else -> "❌ 底层执行出错：\n$e"
        }
    }
}
