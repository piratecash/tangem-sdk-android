package com.tangem.sdk.nfc

import com.tangem.common.nfc.ReadingActiveListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NfcReaderTest {

    private lateinit var reader: NfcReader
    private lateinit var listener: RecordingReadingActiveListener

    @Before
    fun setUp() {
        listener = RecordingReadingActiveListener()
        reader = NfcReader().apply {
            scope = CoroutineScope(Dispatchers.Unconfined)
            this.listener = this@NfcReaderTest.listener
        }
    }

    @Test
    fun startSession_readerModeLost_restoresBeforeActivatingReading() {
        assertReaderModeRestoredBeforeReading(reader::startSession)
    }

    @Test
    fun resumeSession_readerModeLost_restoresBeforeActivatingReading() {
        assertReaderModeRestoredBeforeReading(reader::resumeSession)
    }

    private fun assertReaderModeRestoredBeforeReading(action: () -> Unit) {
        action()

        assertEquals(
            listOf(Event.ReaderModeEnabled, Event.ReadingActive),
            listener.events,
        )
    }

    private class RecordingReadingActiveListener : ReadingActiveListener {
        val events = mutableListOf<Event>()

        override var readingIsActive: Boolean = false
            set(value) {
                field = value
                events += Event.ReadingActive
            }

        override fun onForceEnableReadingMode() {
            events += Event.ReaderModeEnabled
        }

        override fun onForceDisableReadingMode() = Unit
    }

    private enum class Event {
        ReaderModeEnabled,
        ReadingActive,
    }
}
