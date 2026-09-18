/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright © 2021 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package service

import model.AdsCounter
import model.BlockaRepoMessageNotification
import org.junit.Assert
import org.junit.Test

class SerializationServiceTest {
    @Test fun jsonRoundTripsAdsCounter() {
        val original = AdsCounter(persistedValue = 42, runtimeValue = 7)

        val json = JsonSerializationService.serialize(original)
        val restored = JsonSerializationService.deserialize(json, AdsCounter::class)

        Assert.assertEquals(42L, restored.persistedValue)
        Assert.assertEquals(7L, restored.runtimeValue)
        Assert.assertEquals(42L + 7L, original.get())
    }

    @Test fun jsonRoundTripsRepoMessageNotification() {
        val original = BlockaRepoMessageNotification(messageId = "maintenance-1")

        val json = JsonSerializationService.serialize(original)
        val restored = JsonSerializationService.deserialize(json, BlockaRepoMessageNotification::class)

        Assert.assertEquals(original, restored)
    }
}
