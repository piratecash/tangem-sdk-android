package com.tangem.operations.wallet

import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.common.json.MoshiJsonConverter
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class CreateWalletCommandTest {

    private val baseCard = readFixtureCard()

    @Test
    fun performPreCheck_legacyMultiWalletWithoutSelectBlockchain_returnsWalletCannotBeCreated() {
        val error = performPreCheck(
            card(
                firmwareVersion = FirmwareVersion(4, 24),
                isSelectBlockchainAllowed = false,
            ),
        )

        assertTrue(error is TangemSdkError.WalletCannotBeCreated)
    }

    @Test
    fun performPreCheck_createWalletResponseFirmwareWithoutSelectBlockchain_allowsCreateWallet() {
        val error = performPreCheck(
            card(
                firmwareVersion = FirmwareVersion.CreateWalletResponseAvailable,
                isSelectBlockchainAllowed = false,
            ),
        )

        assertNull(error)
    }

    private fun performPreCheck(card: Card): TangemSdkError? {
        val method = CreateWalletCommand::class.java.getDeclaredMethod("performPreCheck", Card::class.java)
        method.isAccessible = true

        return method.invoke(CreateWalletCommand(EllipticCurve.Secp256k1), card) as? TangemSdkError
    }

    private fun card(firmwareVersion: FirmwareVersion, isSelectBlockchainAllowed: Boolean): Card {
        return baseCard.copy(
            firmwareVersion = firmwareVersion,
            supportedCurves = listOf(EllipticCurve.Secp256k1),
            settings = baseCard.settings.copy(
                isSelectBlockchainAllowed = isSelectBlockchainAllowed,
            ),
        )
    }

    private fun readFixtureCard(): Card {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("jsonRpc/Card.json"))
        val json = stream.bufferedReader().use { it.readText() }

        return checkNotNull(MoshiJsonConverter.INSTANCE.fromJson(json))
    }
}
