package com.syncone.health.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncone.health.data.sms.SmsSender
import com.syncone.health.domain.model.SmsMessage
import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.MessageStatus
import com.syncone.health.domain.repository.SmsRepository
import com.syncone.health.domain.usecase.GetThreadsUseCase
import com.syncone.health.domain.usecase.SendSmsReplyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getThreadsUseCase: GetThreadsUseCase,
    private val smsRepository: SmsRepository,
    private val sendSmsReplyUseCase: SendSmsReplyUseCase,
    private val smsSender: SmsSender
) : ViewModel() {

    private val threadId: Long = savedStateHandle.get<Long>("threadId") ?: 0L

    private val _thread = MutableStateFlow<SmsThread?>(null)
    val thread: StateFlow<SmsThread?> = _thread.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _manualReplyText = MutableStateFlow("")
    val manualReplyText: StateFlow<String> = _manualReplyText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        loadThread()
        observeMessages()
    }

    private fun loadThread() {
        viewModelScope.launch {
            val thread = getThreadsUseCase.byId(threadId)
            _thread.value = thread
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            smsRepository.observeMessagesByThreadId(threadId).collect { messages ->
                _messages.value = messages
            }
        }
    }

    fun onManualReplyTextChanged(text: String) {
        _manualReplyText.value = text
    }

    fun sendManualReply() {
        val text = _manualReplyText.value.trim()
        if (text.isEmpty() || _isSending.value) return

        val currentThread = _thread.value ?: return

        viewModelScope.launch {
            _isSending.value = true

            try {
                // Save message
                val messageId = sendSmsReplyUseCase(
                    threadId = threadId,
                    content = text,
                    isManual = true,
                    chwId = "chw_demo" // TODO: Get from auth
                )

                // Send SMS
                val result = smsSender.send(currentThread.phoneNumber, text)

                // Update status
                val status = if (result.isSuccess) {
                    MessageStatus.SENT
                } else {
                    MessageStatus.FAILED
                }
                sendSmsReplyUseCase.updateStatus(messageId, status)

                // Clear input on success
                if (result.isSuccess) {
                    _manualReplyText.value = ""
                }

                Timber.d("Manual reply sent: $result")
            } catch (e: Exception) {
                Timber.e(e, "Failed to send manual reply")
            } finally {
                _isSending.value = false
            }
        }
    }
}
