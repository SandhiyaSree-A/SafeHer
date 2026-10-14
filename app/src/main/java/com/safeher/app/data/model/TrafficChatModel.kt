package com.safeher.app.data.model

data class TrafficRuleItem(
    val id: String,
    val title: String,
    val rule: String,
    val penalty: String,
    val sourceName: String = "Ministry of Road Transport and Highways (MoRTH)",
    val sourceUrl: String = "https://parivahan.gov.in",
    val iconEmoji: String = "🚦",
    val keywords: List<String> = emptyList()
)

data class HelplineItem(
    val id: String,
    val name: String,
    val number: String,
    val category: String,
    val description: String = ""
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: String,
    val trafficRule: TrafficRuleItem? = null,
    val helplines: List<HelplineItem> = emptyList(),
    val quickReplies: List<String> = emptyList(),
    val isEmergency: Boolean = false
)
