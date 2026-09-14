/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DnsDataSourceTest {
    @Test fun removedBlokadaDnsFallsBackToCloudflare() {
        assertFalse(DnsDataSource.getDns().any { it.id == DnsDataSource.blocka.id })
        assertEquals(DnsDataSource.cloudflare, DnsDataSource.byId(DnsDataSource.blocka.id))
    }

    @Test fun plusDnsRemainsAvailableInternally() {
        assertEquals("blocka2", DnsDataSource.blocka.id)
        assertFalse(DnsDataSource.blocka.plusIps.isNullOrEmpty())
    }
}
