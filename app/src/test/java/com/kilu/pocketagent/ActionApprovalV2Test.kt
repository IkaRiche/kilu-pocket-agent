package com.kilu.pocketagent

import com.kilu.pocketagent.core.crypto.ActionApprovalV2
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CREG-003: pure JVM tests of the exact bytes shown and signed by the Approver.
 *  They do not prove biometric interaction on physical hardware. */
class ActionApprovalV2Test {
    private val command = """{"command":["sh","-c","printf 'APPROVED' > /tmp/kilu-approval-test.txt"],"env":{},"step_type":"shell"}"""
    private val digest = "sha256:648fadc8df0e77e075a2f86ba90fec5d9e6041f42a1525b01ac3527f229210a4"

    @Test fun `displayed approved action hashes exact UTF-8 bytes`() {
        assertEquals(digest, ActionApprovalV2.sha256(command))
        ActionApprovalV2.requireActionBinding(command, digest)
    }

    @Test fun `changed action cannot reuse previous approval`() {
        assertFailsWith<IllegalArgumentException> {
            ActionApprovalV2.requireActionBinding(
                command.replace("APPROVED", "ALTERED"), digest)
        }
        assertFailsWith<IllegalArgumentException> {
            ActionApprovalV2.requireActionBinding(null, digest)
        }
        assertFailsWith<IllegalArgumentException> {
            ActionApprovalV2.requireActionBinding(command, null)
        }
    }

    @Test fun `V2 message signs plan AVO action runtime and toolchain without ambiguous framing`() {
        val message = ActionApprovalV2.message(
            "pln_v2","sha256:" + "a".repeat(64),
            "sha256:" + "b".repeat(64),"rt-1","tc-1"
        )
        val expected = "KILU_APPROVAL_V2:6:pln_v2" +
            "71:sha256:" + "a".repeat(64) +
            "71:sha256:" + "b".repeat(64) +
            "4:rt-14:tc-1"
        assertEquals(expected, message.toString(Charsets.UTF_8))
        val changed = ActionApprovalV2.message(
            "pln_v2","sha256:" + "a".repeat(64),
            "sha256:" + "b".repeat(64),"rt-2","tc-1"
        )
        assertTrue(!message.contentEquals(changed))
    }

    @Test fun `UTF-8 fields are length prefixed by bytes not characters`() {
        val message = ActionApprovalV2.message(
            "pln_v2","sha256:" + "a".repeat(64),
            "sha256:" + "b".repeat(64),"rt-λ","tc-1"
        )
        assertTrue(message.toString(Charsets.UTF_8).contains("5:rt-λ"))
    }

    @Test fun `missing scope or invalid hashes fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            ActionApprovalV2.message("pln", "bad","sha256:"+"b".repeat(64),"rt","tc")
        }
        assertFailsWith<IllegalArgumentException> {
            ActionApprovalV2.message("pln","sha256:"+"a".repeat(64),
                "sha256:"+"b".repeat(64),"","tc")
        }
    }
}
