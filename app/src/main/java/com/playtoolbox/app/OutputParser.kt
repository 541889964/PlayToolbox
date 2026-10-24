package com.playtoolbox.app

data class ParsedOutput(val title: String, val body: String, val isError: Boolean, val raw: String)

object OutputParser {
    fun parse(command: String, raw: String, exitOk: Boolean): ParsedOutput {
        if (!exitOk) return ParsedOutput("❌ 执行失败", translateError(raw), true, raw)
        val c = command.trim(); val r = raw.trim()
        if (r.isBlank() || r == "(无输出)") return ParsedOutput("✅ 执行成功", "命令已执行，无输出内容。", false, raw)

        // 彻底汉化返回值
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
            c.startsWith("cat /proc/uptime") -> {
                val sec = r.split(" ").firstOrNull()?.toDoubleOrNull()
                if (sec != null) ParsedOutput("⏱️ 开机时长", "已运行：${(sec / 3600).toInt()} 小时 ${((sec % 3600) / 60).toInt()} 分钟", false, raw)
                else ParsedOutput("⏱️ 开机时长", r, false, raw)
            }
            c.startsWith("pm disable-user") -> ParsedOutput("🧊 冻结成功", "应用已被禁用，无法在后台运行。", false, raw)
            c.startsWith("pm enable") -> ParsedOutput("✅ 解冻成功", "应用已恢复正常使用。", false, raw)
            c.startsWith("pm clear") -> ParsedOutput("🗑️ 数据清空", "应用数据已全部清除。", false, raw)
            c.startsWith("am force-stop") -> ParsedOutput("⛔ 已强停", "应用进程已结束。", false, raw)
            c.startsWith("pm uninstall") -> ParsedOutput("🗑️ 卸载成功", "应用已被卸载。", false, raw)
            c.contains("WifiConfigStore") -> ParsedOutput("🔑 WiFi 密码", "已获取配置文件，请在下方查找 SSID 和 Password：\n\n$r", false, raw)
            c.contains("dumpsys battery") && c.contains("health") -> ParsedOutput("🔋 电池健康", "系统返回代码 $r\n\n2 代表健康，其他数字可能表示过热或老化", false, raw)
            c.contains("dumpsys battery") && c.contains("status") -> ParsedOutput("🔋 充电状态", "状态代码：$r\n\n2 代表正在充电，3 代表放电中，5 代表已充满", false, raw)
            else -> ParsedOutput("✅ 执行结果", r, false, raw)
        }
    }

    private fun translateError(e: String): String {
        val lower = e.lowercase()
        return when {
            lower.contains("uid") && lower.contains("does not have access") -> "权限不足：系统限制了该操作。请确保 App 已获得 Root 权限。"
            lower.contains("permission denied") -> "权限被拒绝：请检查 Root 授权或文件读取权限。"
            lower.contains("su: not found") -> "未找到 su 命令，手机未 Root。"
            lower.contains("no such file") -> "文件或节点不存在，当前手机不支持此功能。"
            else -> "底层拒绝执行：\n$e"
        }
    }
}
