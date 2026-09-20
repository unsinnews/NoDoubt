package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.ThinkingParameterPaths

/** Only paths rooted at request controls are filtered; response schemas remain opaque data. */
internal class ThinkingControls(private val budgetField: String?) {
    private val auxiliaries = listOf("reasoning.summary", "reasoning.exclude", "thinking.display",
        "generationConfig.thinkingConfig.includeThoughts", "thinkingBudgets", "interleavedThinking",
        "chat_template_kwargs.preserve_thinking", "chat_template_args.preserve_thinking", "thinking.clear_thinking")
        .associateBy { normalized(it.split('.')) }

    fun remove(source: JsonObject): JsonObject = filter(source, keepControls = false)
    fun extract(source: JsonObject): JsonObject = filter(source, keepControls = true)

    /** Display preferences and effort-to-budget mappings are independent of the selected strength. */
    fun preserveAuxiliaries(source: JsonObject, target: JsonObject) {
        fun visit(objectValue: JsonObject, parent: List<String>) {
            objectValue.entrySet().forEach { (key, value) ->
                val path = parent + key.split('.')
                val normal = normalized(path)
                val auxiliary = auxiliaries.entries.firstOrNull { normal.take(it.key.size) == it.key }
                if (auxiliary != null) {
                    val relative = path.dropWhile(::isExtraBody)
                    val field = (listOf(auxiliary.value) + relative.drop(auxiliary.key.size)).joinToString(".")
                    // Only the budget map is a compiler helper. Wire auxiliaries retain extraBody's final priority.
                    val destination = if (auxiliary.value == "thinkingBudgets" || !isExtraBody(path.first())) target
                        else target.obj("extraBody") ?: JsonObject().also { target.add("extraBody", it) }
                    destination.merge(jsonObject { putPath(field, value) })
                } else if (value.isJsonObject) visit(value.asJsonObject, path)
            }
        }
        source.entrySet().sortedBy { isExtraBody(it.key.substringBefore('.')) }.forEach { (key, value) ->
            visit(jsonObject { add(key, value) }, emptyList())
        }
    }

    fun hasStrength(source: JsonObject, parent: List<String> = emptyList()): Boolean = source.entrySet().any { (key, value) ->
        val path = parent + key.split('.')
        when {
            normalized(path) in auxiliaries -> false
            value.isJsonObject -> hasStrength(value.asJsonObject, path)
            else -> ThinkingParameterPaths.isThinking(path) || isBudgetField(path)
        }
    }

    private fun normalized(path: List<String>) = path.flatMap { it.split('.') }
        .map { it.replace("_", "").lowercase() }.dropWhile { it == "extrabody" }

    private fun isExtraBody(part: String) = part.replace("_", "").equals("extraBody", ignoreCase = true)

    private fun filter(source: JsonObject, keepControls: Boolean, parent: List<String> = emptyList()): JsonObject = jsonObject {
        source.entrySet().forEach { (key, value) ->
            val path = parent + key.split('.')
            val control = ThinkingParameterPaths.isThinking(path) || isBudgetField(path)
            when {
                control -> if (keepControls) add(key, value.deepCopy())
                value.isJsonObject -> {
                    val nested = filter(value.asJsonObject, keepControls, path)
                    if (nested.size() > 0 || (!keepControls && value.asJsonObject.size() == 0)) add(key, nested)
                }
                !keepControls -> add(key, value.deepCopy())
            }
        }
    }

    private fun isBudgetField(path: List<String>): Boolean {
        val field = budgetField?.split('.') ?: return false
        return path.dropWhile { it.equals("extraBody", ignoreCase = true) } == field
    }
}
