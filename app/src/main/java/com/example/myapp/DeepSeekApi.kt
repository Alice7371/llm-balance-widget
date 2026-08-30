package com.example.myapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object DeepSeekApi {

    enum class Kind { CNY, QUOTA }

    data class Balance(
        val ok: Boolean,
        val title: String,
        val detail: String,
        val value: Double = 0.0,
        val kind: Kind = Kind.CNY
    )

    data class Response(
        val code: Int,
        val body: String
    )

    fun get(url: String, authHeader: String? = null, cookieHeader: String? = null): Response {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (authHeader != null) {
                conn.setRequestProperty("Authorization", authHeader)
            }
            if (cookieHeader != null) {
                conn.setRequestProperty("Cookie", cookieHeader)
            }
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
            } ?: ""
            conn.disconnect()
            Response(code, body)
        } catch (e: Exception) {
            Response(-1, e.message ?: "Network error")
        }
    }

    // ---------- DeepSeek ----------

    fun fetchDeepSeekBalance(apiKey: String): Balance {
        val r = get(
            "https://api.deepseek.com/user/balance",
            authHeader = "Bearer $apiKey"
        )
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseDeepSeek(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private fun parseDeepSeek(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (code == 200) {
            val isAvailable = json.optBoolean("is_available")
            val infos = json.optJSONArray("balance_infos")
            if (infos != null && infos.length() > 0) {
                val info = infos.getJSONObject(0)
                val currency = info.optString("currency", "CNY")
                val total = info.optString("total_balance", "0")
                val granted = info.optString("granted_balance", "0")
                val toppedUp = info.optString("topped_up_balance", "0")
                val status = if (isAvailable) "Available" else "Unavailable"
                return Balance(
                    ok = true,
                    title = "$total $currency",
                    detail = "$status | Topped-up: $toppedUp | Granted: $granted",
                    value = total.toDoubleOrNull() ?: 0.0,
                    kind = Kind.CNY
                )
            }
            return Balance(true, "OK", body)
        }
        val err = json.optString("error", "HTTP $code")
        return Balance(false, "HTTP $code", err)
    }

    // ---------- Kimi / Moonshot ----------

    fun fetchKimiBalance(apiKey: String): Balance {
        val r = get(
            "https://api.moonshot.cn/v1/users/me/balance",
            authHeader = "Bearer $apiKey"
        )
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseKimi(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private fun parseKimi(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (code == 200 && json.optInt("code", -1) == 0) {
            val data = json.getJSONObject("data")
            val available = data.optDouble("available_balance", 0.0)
            val cash = data.optDouble("cash_balance", 0.0)
            val voucher = data.optDouble("voucher_balance", 0.0)
            return Balance(
                ok = true,
                title = String.format("%.2f CNY", available),
                detail = "Cash: %.2f | Voucher: %.2f".format(cash, voucher),
                value = available,
                kind = Kind.CNY
            )
        }
        val err = json.optJSONObject("error")?.optString("message")
            ?: "HTTP $code"
        return Balance(false, "HTTP $code", err)
    }

    // ---------- GLM / Zhipu bigmodel.cn ----------

    /**
     * Coding-plan quota via the monitor endpoint (raw token auth, no Bearer).
     * Live response on a lite plan (2026-08): windows of type CREDIT_LIMIT with
     * unit 3 = hours / unit 6 = weeks, e.g. {"unit":3,"number":5,"percentage":13}
     * for the 5-hour window and {"unit":6,"number":1,"percentage":33} for weekly.
     * No public pay-as-you-go balance endpoint exists (all /users/me/balance
     * variants return 404), so quota is all this provider reports.
     */
    fun fetchGlmBalance(apiKey: String): Balance {
        val r = get(
            "https://open.bigmodel.cn/api/monitor/usage/quota/limit",
            authHeader = apiKey
        )
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseGlmQuota(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private data class GlmWindow(val label: String, val percent: Int, val reset: Long)

    private fun parseGlmQuota(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (!json.optBoolean("success", json.optInt("code", -1) == 200)) {
            return Balance(false, "HTTP $code", json.optString("msg", "Query failed"))
        }
        val data = json.optJSONObject("data")
            ?: return Balance(false, "HTTP $code", "No data")
        val limits = data.optJSONArray("limits")
            ?: return Balance(false, "HTTP $code", "No limits")

        val windows = mutableListOf<GlmWindow>()
        var mcp: JSONObject? = null
        for (i in 0 until limits.length()) {
            val item = limits.optJSONObject(i) ?: continue
            if (item.optString("type") == "TIME_LIMIT") {
                mcp = item
                continue
            }
            val percent = item.optInt("percentage", -1)
            if (percent < 0) continue
            val unit = item.optInt("unit", -1)
            val number = item.optInt("number", 1)
            val label = when {
                unit == 3 -> "${number}h"
                unit == 6 -> "${number * 7}d"
                else -> ""
            }
            windows.add(GlmWindow(label, percent, item.optLong("nextResetTime", Long.MAX_VALUE)))
        }
        if (windows.isEmpty()) return Balance(false, "HTTP $code", "No quota windows")

        // Soonest-resetting window first (5h before weekly) even when the
        // unit codes are unrecognized.
        windows.sortBy { it.reset }
        val title = windows.withIndex().joinToString(" | ") { (idx, w) ->
            val name = w.label.ifEmpty { if (idx == 0) "5h" else "7d" }
            "$name ${w.percent}%"
        }

        val detailParts = mutableListOf<String>()
        val level = data.optString("level", "")
        if (level.isNotEmpty()) detailParts.add("Plan: $level")
        mcp?.let {
            val used = it.optLong("currentValue", -1L)
            val total = it.optLong("usage", -1L)
            if (used >= 0 && total > 0) detailParts.add("MCP: $used/$total")
        }
        return Balance(
            ok = true,
            title = title,
            detail = detailParts.joinToString(" | ").ifEmpty { "GLM coding plan" },
            value = windows.maxOf { it.percent }.toDouble(),
            kind = Kind.QUOTA
        )
    }

    // ---------- Xiaomi MiMo (cookie-based console API) ----------

    fun fetchMimoBalance(cookie: String): Balance {
        val r = get(
            "https://platform.xiaomimimo.com/api/v1/balance",
            cookieHeader = cookie
        )
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseMimo(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private fun parseMimo(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (code == 200) {
            val data = json.optJSONObject("data")
            if (data != null) {
                val currency = data.optString("currency", "CNY")
                val balance = data.optString("balance", "")
                val available = data.optString("availableBalance", "")
                val main = if (balance.isNotEmpty()) balance else available
                if (main.isNotEmpty()) {
                    val numeric = (balance.ifEmpty { available })
                        .toDoubleOrNull() ?: 0.0
                    return Balance(
                        true,
                        "$main $currency",
                        "MiMo balance",
                        value = numeric,
                        kind = Kind.CNY
                    )
                }
            }
            return Balance(true, "OK", body)
        }
        val err = json.optString("message", "HTTP $code")
        return Balance(false, "HTTP $code", err)
    }

    // ---------- OpenCode Go (subscription quota) ----------

    fun fetchOpenCodeGo(apiKey: String): Balance {
        val r = get(
            "https://opencode.ai/zen/go/v1/usage",
            authHeader = "Bearer $apiKey"
        )
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseOpenCodeGo(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private fun parseOpenCodeGo(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (code == 200) {
            val usage = json.optJSONObject("usage") ?: return Balance(false, "Error", "No usage field")
            fun fmt(key: String): String {
                val win = usage.optJSONObject(key) ?: return "-"
                val percent = win.optInt("percent", -1)
                if (percent < 0) return "-"
                val status = win.optString("status", "ok")
                return if (status == "rate-limited") "LIMIT" else "$percent%"
            }
            val rolling = fmt("rolling")
            val weekly = fmt("weekly")
            val monthly = fmt("monthly")
            val monthlyPercent = usage.optJSONObject("monthly")?.optInt("percent", -1) ?: -1
            return Balance(
                ok = true,
                title = "5h $rolling | 7d $weekly",
                detail = "Monthly: $monthly (quota windows)",
                value = if (monthlyPercent >= 0) monthlyPercent.toDouble() else 0.0,
                kind = Kind.QUOTA
            )
        }
        val err = json.optString("error", "HTTP $code")
        return Balance(false, "HTTP $code", err)
    }

    // ---------- Custom provider (user-supplied URL + sk- key) ----------

    fun fetchCustomBalance(url: String, apiKey: String): Balance {
        val r = get(url, authHeader = "Bearer $apiKey")
        if (r.code < 0) return Balance(false, "Error", r.body)
        return try {
            parseCustom(r.code, r.body)
        } catch (e: Exception) {
            Balance(false, "Error", "Parse error: ${e.message}")
        }
    }

    private fun parseCustom(code: Int, body: String): Balance {
        val json = JSONObject(body)
        if (code == 200) {
            val value = findBalance(json)
            if (value != null) {
                return Balance(
                    ok = true,
                    title = "%.2f CNY".format(value),
                    detail = "Custom provider",
                    value = value,
                    kind = Kind.CNY
                )
            }
            return Balance(true, "OK", body.take(80))
        }
        val err = json.optString("error", "HTTP $code")
        return Balance(false, "HTTP $code", err)
    }

    /** Recursively look for a numeric balance in common response shapes. */
    private fun findBalance(node: Any?): Double? {
        when (node) {
            is JSONObject -> {
                for (key in arrayOf(
                    "balance", "total_balance", "available_balance",
                    "availableBalance", "balance_amount", "balance_infos"
                )) {
                    if (key == "balance_infos") {
                        val infos = node.optJSONArray(key)
                        if (infos != null && infos.length() > 0) {
                            findBalance(infos.getJSONObject(0))?.let { return it }
                        }
                        continue
                    }
                    when (val v = node.opt(key)) {
                        is Double -> return v
                        is Int -> return v.toDouble()
                        is Long -> return v.toDouble()
                        is String -> v.toDoubleOrNull()?.let { return it }
                    }
                }
                node.optJSONObject("data")?.let { findBalance(it)?.let { v -> return v } }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findBalance(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }
}