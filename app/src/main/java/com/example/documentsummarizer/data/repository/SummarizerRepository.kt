package com.example.documentsummarizer.data.repository

import com.example.documentsummarizer.BuildConfig
import com.example.documentsummarizer.data.openai.NetworkModule
import com.example.documentsummarizer.data.openai.OpenAIService
import com.example.documentsummarizer.data.openai.ResponsesRequest
import com.example.documentsummarizer.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SummarizerRepository(
    private val api: OpenAIService = NetworkModule.openAI,
    private val model: String = "gpt-4o-mini"
) {

    suspend fun summarize(ocrText: String): String = withContext(Dispatchers.IO) {
        Log.d({ "OpenAI called" })
        val apiKey = BuildConfig.OPENAI_API_KEY
        Log.d({ "OpenAI API Key (masked): ${if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else apiKey}" })
        val clean = ocrText.trim()
        if (clean.isBlank()) return@withContext "No text detected."

        val chunks = chunkText(clean, 6000)
        val partials = chunks.mapIndexed { idx, part ->
            singleSummary(
                """
                You are a concise document summarizer. 
                Summarize the following text into 5–8 bullet points, preserving key facts, numbers, and entities. 
                If the text is noisy OCR, ignore garbled parts.
                Return only the bullet points.
                
                [CHUNK ${idx + 1}/${chunks.size}]
                $part
                """.trimIndent()
            )
        }

        // Reduce step
        singleSummary(
            """
            Merge the following bullet-point summaries into a single, non-redundant summary (6–10 bullets max). 
            Prefer clarity and factual accuracy. Include a 1-line title at the top.

            ${partials.joinToString("\n\n")}
            """.trimIndent()
        )
    }

    private suspend fun singleSummary(prompt: String): String {
        return try {
            val req = ResponsesRequest(
                model = model,
                input = prompt,
                temperature = 0.2
            )
            val res = api.responses("Bearer ${BuildConfig.OPENAI_API_KEY}", req)
            val text = res.output
                ?.flatMap { it.content ?: emptyList() }
                ?.joinToString("\n") { it.text.orEmpty() }
                ?.trim()
            text?.ifBlank { "(empty)" } ?: "(no output)"
        } catch (e: Exception) {
            Log.d({ "OpenAI error: ${e.message}" })
            "(error: ${e.message})"
        }
    }

    private fun chunkText(s: String, maxChars: Int): List<String> {
        if (s.length <= maxChars) return listOf(s)
        val parts = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val end = (i + maxChars).coerceAtMost(s.length)
            // try to end at sentence
            var cut = s.lastIndexOf('.', end)
            if (cut <= i) cut = end
            parts.add(s.substring(i, cut).trim())
            i = cut
        }
        return parts.filter { it.isNotBlank() }
    }
}
