package com.nodoubt.core.config

import com.nodoubt.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LegacyMigrationTest {
    @Test fun standaloneThemePreferencesMigrateWithoutInventingAChannel() {
        val result = LegacyMigration.migrate(emptyMap<String, Any>(), mapOf(
            "float_size" to 64, "user_nickname" to "旧昵称", "avatar_index" to 3, "current_theme" to "light_brown_black"
        ))
        assertTrue(result.config.providers.isEmpty())
        assertEquals("旧昵称", result.config.preferences.nickname)
        assertEquals("rocket", result.config.preferences.avatar)
        assertEquals(64, result.config.preferences.bubbleSizeDp)
        assertEquals(ThemeFamily.PICOVE, result.config.preferences.theme)
    }

    @Test fun masterPreferencesPreservePoolsSelectionsAndSecrets() {
        val result = LegacyMigration.migrate(mapOf(
            "api_key" to "master-key", "base_url" to "https://legacy.example/v1",
            "ocr_model_id" to "vision", "ocr_model_list" to "vision\ntext-model",
            "fast_model_id" to "fast-b", "fast_model_list" to "fast-a\nfast-b",
            "deep_model_id" to "deep", "auto_delete_screenshot" to false
        ))
        val channel = result.config.providers.single()
        assertEquals("https://legacy.example/v1", channel.baseUrl)
        assertEquals("master-key", result.credentials[channel.credentialRef])
        assertEquals(listOf("fast-a", "fast-b"), result.config.bindings.getValue(Usage.FAST).entries.map { it.modelId })
        assertEquals("fast-b", result.config.bindings.getValue(Usage.FAST).selected!!.modelId)
        assertFalse(result.config.preferences.autoDeleteScreenshot)
        assertTrue(channel.models.any { it.id == "text-model" })
    }

    @Test fun defaultChannelIntermediateFormatWinsOverLegacyFields() {
        val result = LegacyMigration.migrate(mapOf(
            "default_channel_id" to "custom", "default_channel_name" to "中间渠道",
            "default_channel_base_url" to "https://new.example", "base_url" to "https://old.example",
            "default_channel_api_key" to "new-key", "api_key" to "old-key",
            "default_channel_fast_model_id" to "manual-model"
        ))
        val channel = result.config.providers.single()
        assertEquals("custom", channel.id)
        assertEquals("中间渠道", channel.name)
        assertEquals("https://new.example", channel.baseUrl)
        assertEquals("new-key", result.credentials[channel.credentialRef])
        assertEquals("manual-model", result.config.bindings.getValue(Usage.FAST).selected!!.modelId)
    }

    @Test fun channelJsonPreservesCrossChannelFallbackOrderWithoutCapabilityFiltering() {
        val result = LegacyMigration.migrate(mapOf(
            "channels_json" to """[{"id":"a","name":"A","baseUrl":"https://a.example","apiKey":"a-key"},
              {"id":"b","name":"B","baseUrl":"https://b.example","apiKey":"b-key"}]""",
            "usage_model_pools_json" to """[{"usage":"ocr","selectedChannelId":"b","selectedModelId":"text-only",
              "entries":[{"channelId":"a","modelId":"m1"},{"channelId":"b","modelId":"text-only"}]}]"""
        ))
        assertEquals(2, result.config.providers.size)
        val ocr = result.config.bindings.getValue(Usage.OCR)
        assertEquals(listOf("a", "b"), ocr.entries.map { it.channelId })
        assertEquals(ModelRef("b", "text-only"), ocr.selected)
        assertEquals("text-only", result.config.providers[1].models.single().id)
    }

    @Test fun corruptLegacyDataIsNotSilentlyReplacedByDefaults() {
        assertTrue(runCatching { LegacyMigration.migrate(mapOf("channels_json" to "{bad")) }.isFailure)
        assertTrue(runCatching { LegacyMigration.migrate(mapOf("channels_json" to "[]", "usage_model_pools_json" to "{bad")) }.isFailure)
    }
}
