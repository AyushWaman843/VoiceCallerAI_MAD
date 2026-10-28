package com.ayush.madv2.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class ConversationFlowTest {
    @Test
    fun clearedComposerRetainsPromptThroughConfirmationWithoutDuplicateSubmits() {
        // Local HTTP fixture exercises the real Retrofit serialization, never a paid service.
        val server = ServerSocket(0)
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newSingleThreadExecutor()
        val preview = """{"status":"needs_confirmation","contact_name":"Mom","contact_number":"+910000000000","message_preview":"Meeting reminder","scheduled_time":"2026-10-01T19:00:00+05:30","agent_result":{"contact_name":"Mom","contact_number":"+910000000000","message":"Meeting reminder","rephrased_message":"Meeting reminder","scheduled_time":"2026-10-01T19:00:00+05:30","time_extracted":true,"missing_fields":[]}}"""
        executor.submit {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine()
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        val body = CharArray(length)
                        var offset = 0
                        while (offset < length) {
                            val count = reader.read(body, offset, length-offset)
                            if (count < 0) break
                            offset += count
                        }
                        val response = if (first.contains("/schedule-call")) {
                            val raw = String(body)
                            requests.add(raw)
                            if (JsonParser.parseString(raw).asJsonObject["confirmed"].asBoolean)
                                """{"status":"scheduled","job_id":"fixture-call","contact_name":"Mom","call_time":"2026-10-01T19:00:00+05:30"}"""
                            else preview
                        } else """{"upcoming":[],"past":[]}"""
                        val bytes = response.toByteArray()
                        val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().apply { write(headers.toByteArray()); write(bytes); flush() }
                    }
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: CallAiViewModel
        val prompt = "Call Mom tomorrow at 7 PM about our meeting."
        try {
            instrumentation.runOnMainSync {
                vm = CallAiViewModel(initialState = CallAiUiState(backendBaseUrl = "http://127.0.0.1:${server.localPort}/"))
                vm.setPrompt(prompt)
                vm.submitPrompt()
                vm.submitPrompt()
            }
            await { vm.uiState.value.confirmationDraft != null }
            assertEquals("", vm.uiState.value.prompt)
            assertEquals(prompt, vm.uiState.value.conversation.single().text)
            assertEquals(1, requests.size)
            instrumentation.runOnMainSync {
                vm.confirmSchedule()
                vm.confirmSchedule()
            }
            await { vm.uiState.value.conversation.any { it.callId == "fixture-call" } }
            assertEquals(2, requests.size)
            val confirmation = JsonParser.parseString(requests[1]).asJsonObject
            assertEquals(prompt, confirmation["raw_prompt"].asString)
            assertTrue(confirmation["confirmed"].asBoolean)
            assertEquals("Mom", confirmation["agent_result"].asJsonObject["contact_name"].asString)
            assertEquals(AppTab.Home, vm.uiState.value.selectedTab)
            await { !vm.uiState.value.isRefreshingCalls }
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertTrue("Timed out waiting for ViewModel response", condition())
    }
}
