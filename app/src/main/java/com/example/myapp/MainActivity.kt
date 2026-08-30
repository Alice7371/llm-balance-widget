package com.example.myapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var providers = ProviderId.values().toMutableList()
    private lateinit var providerOrderContainer: LinearLayout
    private lateinit var customContainer: LinearLayout
    private var enabledProviders: MutableSet<ProviderId> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiKeyInput = findViewById<EditText>(R.id.api_key_input)
        val statusText = findViewById<TextView>(R.id.status_text)
        providerOrderContainer = findViewById(R.id.provider_order_container)
        customContainer = findViewById(R.id.custom_container)

        val spinner = findViewById<Spinner>(R.id.provider_spinner)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            providers.map { it.displayName }
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = providers[position]
                apiKeyInput.setText(getCredential(provider.key))
                apiKeyInput.hint = when (provider) {
                    ProviderId.MIMO -> "Cookie: api-platform_serviceToken=...; userId=..."
                    ProviderId.GLM -> "API key (id.secret) from open.bigmodel.cn"
                    else -> "sk-..."
                }
                statusText.text = credentialStatus(provider)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            val provider = providers[spinner.selectedItemPosition]
            val value = apiKeyInput.text.toString().trim()
            if (value.isBlank()) {
                clearCredential(provider.key)
                statusText.text = "Credential cleared for ${provider.displayName}."
                Toast.makeText(this, "${provider.displayName} cleared", Toast.LENGTH_SHORT).show()
            } else {
                saveCredential(provider.key, value)
                statusText.text = "Saved for ${provider.displayName}."
                Toast.makeText(this, "${provider.displayName} saved", Toast.LENGTH_SHORT).show()
            }
            refreshAllWidgets()
        }

        renderOrderEditor()
        renderCustomProviders()

        findViewById<Button>(R.id.add_custom_button).setOnClickListener {
            val name = findViewById<EditText>(R.id.custom_name_input).text.toString().trim()
            val url = findViewById<EditText>(R.id.custom_url_input).text.toString().trim()
            val key = findViewById<EditText>(R.id.custom_key_input).text.toString().trim()
            if (name.isEmpty() || url.isEmpty() || key.isEmpty()) {
                Toast.makeText(this, "Fill in name, URL and API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http")) {
                Toast.makeText(this, "URL must start with http(s)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val custom = readCustomProviders()
            custom.add(CustomProvider(name, url, key))
            saveCustomProviders(custom)
            findViewById<EditText>(R.id.custom_name_input).setText("")
            findViewById<EditText>(R.id.custom_url_input).setText("")
            findViewById<EditText>(R.id.custom_key_input).setText("")
            renderCustomProviders()
            refreshAllWidgets()
        }
    }

    private fun renderOrderEditor() {
        providerOrderContainer.removeAllViews()
        enabledProviders = readEnabledProviders(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
        providers.forEachIndexed { index, provider ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp, 0, 4.dp)
            }

            val check = CheckBox(this).apply {
                text = provider.displayName
                isChecked = provider in enabledProviders
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        enabledProviders.add(provider)
                    } else {
                        enabledProviders.remove(provider)
                    }
                    saveEnabledProviders()
                    refreshAllWidgets()
                }
            }

            val up = Button(this).apply {
                text = "▲"
                setOnClickListener { moveProvider(index, -1) }
            }
            val down = Button(this).apply {
                text = "▼"
                setOnClickListener { moveProvider(index, 1) }
            }

            row.addView(check, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(up)
            row.addView(down)
            providerOrderContainer.addView(row)
        }
    }

    private fun moveProvider(index: Int, delta: Int) {
        val target = index + delta
        if (target < 0 || target >= providers.size) return
        val tmp = providers[index]
        providers[index] = providers[target]
        providers[target] = tmp
        saveProviderOrder()
        renderOrderEditor()
        refreshAllWidgets()
    }

    private fun saveProviderOrder() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, providers.joinToString(",") { it.name })
            .apply()
    }

    private fun refreshAllWidgets() {
        sendBroadcast(
            Intent(this, CombinedBalanceWidgetProvider::class.java).apply {
                action = CombinedBalanceWidgetProvider.ACTION_REFRESH
            }
        )
    }

    private fun getCredential(key: String): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, null).orEmpty()

    private fun saveCredential(key: String, value: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun clearCredential(key: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun saveEnabledProviders() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ENABLED, enabledProviders.joinToString(",") { it.name })
            .apply()
    }

    private fun readEnabledProviders(prefs: SharedPreferences): MutableSet<ProviderId> {
        val saved = prefs.getString(KEY_ENABLED, null)
        if (saved.isNullOrBlank()) return ProviderId.values().toMutableSet()
        val parsed = saved.split(",").mapNotNull { name ->
            ProviderId.entries.firstOrNull { it.name == name }
        }.toMutableSet()
        return if (parsed.isEmpty()) ProviderId.values().toMutableSet() else parsed
    }

    private fun credentialStatus(provider: ProviderId): String {
        val set = getCredential(provider.key).isNotEmpty()
        return if (set) {
            "${provider.displayName} credential is set."
        } else {
            "Enter your ${provider.displayName} credential to enable it."
        }
    }

    private fun renderCustomProviders() {
        customContainer.removeAllViews()
        val custom = readCustomProviders()
        custom.forEachIndexed { index, provider ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp, 0, 4.dp)
            }

            val info = TextView(this).apply {
                text = "${provider.name}  ${provider.url}"
                textSize = 13f
            }

            val remove = Button(this).apply {
                text = "✕"
                setOnClickListener {
                    val updated = readCustomProviders().toMutableList()
                    if (index in updated.indices) {
                        updated.removeAt(index)
                        saveCustomProviders(updated)
                        renderCustomProviders()
                        refreshAllWidgets()
                    }
                }
            }

            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(remove)
            customContainer.addView(row)
        }
    }

    private fun readCustomProviders(): MutableList<CustomProvider> =
        CustomProvider.listFromJson(
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CUSTOM, null)
        ).toMutableList()

    private fun saveCustomProviders(list: List<CustomProvider>) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM, CustomProvider.listToJson(list))
            .apply()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS_NAME = "myapp_prefs"
        const val KEY_DEEPSEEK = "deepseek_api_key"
        const val KEY_KIMI = "kimi_api_key"
        const val KEY_MIMO = "mimo_cookie"
        const val KEY_OPENCODE_GO = "opencode_go_api_key"
        const val KEY_GLM = "glm_api_key"
        const val KEY_ORDER = "provider_order"
        const val KEY_ENABLED = "provider_enabled"
        const val KEY_CUSTOM = "custom_providers"

        fun readProviderOrder(prefs: SharedPreferences): List<ProviderId> {
            val order = prefs.getString(KEY_ORDER, null)
            if (order.isNullOrBlank()) return ProviderId.values().toList()
            val parsed = order.split(",").mapNotNull { name ->
                ProviderId.entries.firstOrNull { it.name == name }
            }
            if (parsed.isEmpty()) return ProviderId.values().toList()
            // A saved order lists every provider that existed when it was saved,
            // so anything missing was added by a newer app version: append it.
            val saved = parsed.toSet()
            return parsed + ProviderId.values().filter { it !in saved }
        }

        fun readEnabledProviders(prefs: SharedPreferences): Set<ProviderId> {
            val saved = prefs.getString(KEY_ENABLED, null)
            if (saved.isNullOrBlank()) return ProviderId.values().toSet()
            val parsed = saved.split(",").mapNotNull { name ->
                ProviderId.entries.firstOrNull { it.name == name }
            }.toSet()
            return if (parsed.isEmpty()) ProviderId.values().toSet() else parsed
        }
    }
}