package com.tangem.operations.attestation.service

import com.tangem.common.CompletionResult
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse

/**
 * Service for online attestation process
 */
internal interface OnlineAttestationService {

    suspend fun attestCard(cardId: String, cardPublicKey: ByteArray): CompletionResult<CardVerificationInfoResponse>
}
