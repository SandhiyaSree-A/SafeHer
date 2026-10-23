package com.safeher.app.ui.chatbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.model.ChatMessage
import com.safeher.app.data.offline.ConnectivityRepository
import com.safeher.app.data.repository.TrafficKnowledgeBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatbotViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivityRepository = ConnectivityRepository.get(application)
    val isOnline: StateFlow<Boolean> = connectivityRepository.isOnline

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    init {
        resetWelcomeMessage()
    }

    private fun resetWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            id = "welcome",
            isUser = false,
            text = "Hi! I'm SHEVA 💗\nHow can I help you today?",
            timestamp = getCurrentTime(),
            quickReplies = emptyList()
        )
        _messages.value = listOf(welcomeMessage)
    }

    private fun getCurrentTime(): String = timeFormat.format(Date())

    fun sendMessage(inputText: String) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || _isTyping.value) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = trimmed,
            timestamp = getCurrentTime()
        )

        _messages.value = _messages.value + userMessage
        _isTyping.value = true

        viewModelScope.launch {
            delay(500)

            val onlineStatus = connectivityRepository.currentlyOnline()
            val (rule, helplines) = TrafficKnowledgeBase.findAnswer(trimmed)

            val botResponse = if (rule != null) {
                val headerPrefix = if (onlineStatus) {
                    "Here is the verified traffic regulation according to the Motor Vehicles Act:"
                } else {
                    "[Offline Knowledge Base] Verified Indian Traffic Regulation:"
                }

                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = headerPrefix,
                    timestamp = getCurrentTime(),
                    trafficRule = rule,
                    helplines = helplines,
                    quickReplies = listOf(
                        "🚦 Explain Traffic Signs",
                        "⛑️ Helmet Regulations",
                        "🚑 Road Accident Help"
                    )
                )
            } else if (helplines.isNotEmpty()) {
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = "Here are the verified emergency assistance numbers:",
                    timestamp = getCurrentTime(),
                    helplines = helplines,
                    quickReplies = listOf(
                        "🚦 Explain Traffic Signs",
                        "⛑️ Helmet Regulations",
                        "🚑 Road Accident Help"
                    ),
                    isEmergency = true
                )
            } else {
                val fallbackText = if (onlineStatus) {
                    "I could not locate a specific traffic rule for '$trimmed'. Please dial 112 in emergencies."
                } else {
                    "[Offline Mode] I could not find that specific rule in the local offline database. Please dial 112 in emergencies."
                }

                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = fallbackText,
                    timestamp = getCurrentTime(),
                    quickReplies = listOf(
                        "🚦 Explain Traffic Signs",
                        "⛑️ Helmet Regulations",
                        "🚑 Road Accident Help"
                    )
                )
            }

            _isTyping.value = false
            _messages.value = _messages.value + botResponse
        }
    }
}
