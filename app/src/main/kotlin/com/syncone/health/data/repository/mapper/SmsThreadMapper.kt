package com.syncone.health.data.repository.mapper

import com.syncone.health.data.local.database.entity.SmsThreadEntity
import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel

/**
 * Maps between SmsThreadEntity (data layer) and SmsThread (domain layer).
 */
object SmsThreadMapper {

    fun toDomain(entity: SmsThreadEntity, lastMessage: String = ""): SmsThread {
        return SmsThread(
            id = entity.id,
            phoneNumber = entity.phoneNumber,
            status = ThreadStatus.valueOf(entity.status),
            urgencyLevel = UrgencyLevel.valueOf(entity.urgencyLevel),
            lastMessageAt = entity.lastMessageAt,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            messageCount = entity.messageCount,
            lastMessage = lastMessage
        )
    }

    fun toEntity(domain: SmsThread): SmsThreadEntity {
        return SmsThreadEntity(
            id = domain.id,
            phoneNumber = domain.phoneNumber,
            status = domain.status.name,
            urgencyLevel = domain.urgencyLevel.name,
            lastMessageAt = domain.lastMessageAt,
            createdAt = domain.createdAt,
            expiresAt = domain.expiresAt,
            messageCount = domain.messageCount
        )
    }
}
