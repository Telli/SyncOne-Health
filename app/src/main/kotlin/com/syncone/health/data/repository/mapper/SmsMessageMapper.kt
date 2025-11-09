package com.syncone.health.data.repository.mapper

import com.syncone.health.data.local.database.entity.SmsMessageEntity
import com.syncone.health.domain.model.SmsMessage
import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus

/**
 * Maps between SmsMessageEntity (data layer) and SmsMessage (domain layer).
 */
object SmsMessageMapper {

    fun toDomain(entity: SmsMessageEntity): SmsMessage {
        return SmsMessage(
            id = entity.id,
            threadId = entity.threadId,
            content = entity.content,
            direction = MessageDirection.valueOf(entity.direction),
            status = MessageStatus.valueOf(entity.status),
            timestamp = entity.timestamp,
            aiConfidence = entity.aiConfidence,
            isManual = entity.isManual
        )
    }

    fun toEntity(domain: SmsMessage): SmsMessageEntity {
        return SmsMessageEntity(
            id = domain.id,
            threadId = domain.threadId,
            content = domain.content,
            direction = domain.direction.name,
            status = domain.status.name,
            timestamp = domain.timestamp,
            aiConfidence = domain.aiConfidence,
            isManual = domain.isManual
        )
    }
}
