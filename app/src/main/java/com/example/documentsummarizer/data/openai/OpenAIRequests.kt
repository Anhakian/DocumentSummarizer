package com.example.documentsummarizer.data.openai

data class ResponsesRequest(
    val model: String,
    val input: String,
    val temperature: Double = 0.2
)

data class ResponsesMessage(
    val role: String,
    val content: String
)

data class ResponsesOutputContent(val text: String?)
data class ResponsesOutputItem(val content: List<ResponsesOutputContent>?)
data class ResponsesResponse(
    val id: String?,
    val output: List<ResponsesOutputItem>?
)