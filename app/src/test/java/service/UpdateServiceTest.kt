/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package service

import model.BlockaRepoMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpdateServiceTest {
    private val today = LocalDate.of(2026, 9, 15)
    private val message = BlockaRepoMessage(
        id = "maintenance-1",
        title = "Maintenance update",
        body = "Blokada has been updated."
    )

    @Test fun acceptsCompleteUnseenMessage() {
        assertEquals(
            RepoMessageEligibility.ELIGIBLE,
            repoMessageEligibility(message, seenId = "", today = today)
        )
    }

    @Test fun rejectsMissingFields() {
        listOf(
            message.copy(id = " "),
            message.copy(title = ""),
            message.copy(body = " ")
        ).forEach {
            assertEquals(
                RepoMessageEligibility.MISSING_FIELDS,
                repoMessageEligibility(it, seenId = "", today = today)
            )
        }
    }

    @Test fun rejectsSeenMessage() {
        assertEquals(
            RepoMessageEligibility.ALREADY_SEEN,
            repoMessageEligibility(message, seenId = message.id, today = today)
        )
    }

    @Test fun acceptsMessageUntilEndOfExpiryDate() {
        assertEquals(
            RepoMessageEligibility.ELIGIBLE,
            repoMessageEligibility(message.copy(expires = "2026-09-15"), seenId = "", today = today)
        )
    }

    @Test fun rejectsDeferredMessageAfterExpiryDate() {
        val expiringMessage = message.copy(expires = "2026-09-15")
        assertEquals(
            RepoMessageEligibility.ELIGIBLE,
            repoMessageEligibility(expiringMessage, seenId = "", today = today)
        )
        assertEquals(
            RepoMessageEligibility.EXPIRED,
            repoMessageEligibility(expiringMessage, seenId = "", today = today.plusDays(1))
        )
    }

    @Test fun rejectsExpiredOrMalformedMessage() {
        assertEquals(
            RepoMessageEligibility.EXPIRED,
            repoMessageEligibility(message.copy(expires = "2026-09-14"), seenId = "", today = today)
        )
        assertEquals(
            RepoMessageEligibility.INVALID_EXPIRY,
            repoMessageEligibility(message.copy(expires = "15 September"), seenId = "", today = today)
        )
    }

    @Test fun acceptsOnlySafeMessageLinks() {
        assertEquals("https://blokada.org/help", validRepoMessageUrl("https://blokada.org/help"))
        assertEquals(null, validRepoMessageUrl("http://blokada.org/help"))
        assertEquals(null, validRepoMessageUrl("https://user:secret@blokada.org/help"))
        assertEquals(null, validRepoMessageUrl("market://details?id=org.blokada"))
        assertEquals(null, validRepoMessageUrl("not a url"))
    }
}
