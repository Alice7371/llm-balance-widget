package com.example.myapp

import org.json.JSONArray
import org.json.JSONObject

enum class ProviderId(
    val displayName: String,
    val key: String,
    val kind: DeepSeekApi.Kind
) {
    DEEPSEEK("DeepSeek", MainActivity.KEY_DEEPSEEK, DeepSeekApi.Kind.CNY),
    KIMI("Kimi", MainActivity.KEY_KIMI, DeepSeekApi.Kind.CNY),
    MIMO("MiMo", MainActivity.KEY_MIMO, DeepSeekApi.Kind.CNY),
    OPENCODEGO("OpenCode Go", MainActivity.KEY_OPENCODE_GO, DeepSeekApi.Kind.QUOTA),
    GLM("GLM", MainActivity.KEY_GLM, DeepSeekApi.Kind.CNY);

    fun fetch(credential: String): DeepSeekApi.Balance = when (this) {
        DEEPSEEK -> DeepSeekApi.fetchDeepSeekBalance(credential)
        KIMI -> DeepSeekApi.fetchKimiBalance(credential)
        MIMO -> DeepSeekApi.fetchMimoBalance(credential)
        OPENCODEGO -> DeepSeekApi.fetchOpenCodeGo(credential)
        GLM -> DeepSeekApi.fetchGlmBalance(credential)
    }
}

data class CustomProvider(
    val name: String,
    val url: String,
    val apiKey: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("url", url)
        .put("apiKey", apiKey)

    fun fetch(): DeepSeekApi.Balance = DeepSeekApi.fetchCustomBalance(url, apiKey)

    companion object {
        fun fromJson(o: JSONObject): CustomProvider =
            CustomProvider(
                o.optString("name"),
                o.optString("url"),
                o.optString("apiKey")
            )

        fun listToJson(list: List<CustomProvider>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<CustomProvider> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}