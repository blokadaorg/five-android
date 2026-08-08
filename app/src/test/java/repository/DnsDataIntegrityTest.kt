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

package repository

import model.Dns
import org.junit.Test
import java.net.InetAddress
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Data integrity tests - validates DNS configuration structure
 *
 * Run with: ./gradlew testDebugUnitTest -k "DnsDataIntegrity"
 */
class DnsDataIntegrityTest {

    @Test
    fun testAllServersHaveRequiredFields() {
        DnsDataSource.getDns().forEach { dns ->
            assertNotNull(dns.id, "DNS server missing ID")
            assertTrue(dns.id.isNotEmpty(), "DNS server has empty ID")

            assertNotNull(dns.label, "DNS server ${dns.id} missing label")
            assertTrue(dns.label.isNotEmpty(), "DNS server ${dns.id} has empty label")

            assertTrue(dns.ips.isNotEmpty(), "DNS server ${dns.id} has no IPs")

            if (dns.isDnsOverHttps()) {
                assertNotNull(dns.name, "DoH server ${dns.id} missing hostname")
                assertTrue(dns.name.isNotEmpty(), "DoH server ${dns.id} has empty hostname")

                assertNotNull(dns.path, "DoH server ${dns.id} missing path")
                assertTrue(dns.path.isNotEmpty(), "DoH server ${dns.id} has empty path")
            }
        }
    }

    @Test
    fun testNoDuplicateDnsIds() {
        val ids = DnsDataSource.getDns().map { it.id }
        val uniqueIds = ids.distinct()
        assertTrue(
            ids.size == uniqueIds.size,
            "Duplicate DNS server IDs found: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }}"
        )
    }

    @Test
    fun testValidIpAddresses() {
        DnsDataSource.getDns().forEach { dns ->
            dns.ips.forEach { ip ->
                try {
                    InetAddress.getByName(ip)
                } catch (e: Exception) {
                    throw AssertionError("Invalid IP address '$ip' in server ${dns.id}: ${e.message}", e)
                }
            }
        }
    }

    @Test
    fun testValidRegions() {
        val validRegions = setOf("worldwide", "europe", "northamerica", "other")
        DnsDataSource.getDns().forEach { dns ->
            assertTrue(
                dns.region in validRegions,
                "Invalid region '${dns.region}' in server ${dns.id}"
            )
        }
    }

    @Test
    fun testByIdFallback() {
        // Test that byId falls back to Cloudflare for unknown IDs
        val unknownDns = DnsDataSource.byId("nonexistent-id-12345")
        assertTrue(
            unknownDns.id == DnsDataSource.cloudflare.id,
            "byId should fallback to Cloudflare for unknown IDs, got ${unknownDns.id}"
        )
    }

    @Test
    fun testNetworkDnsHandling() {
        val networkDns = DnsDataSource.byId(DnsDataSource.network.id)
        assertTrue(
            networkDns.id == DnsDataSource.network.id,
            "byId should return network DNS for network ID"
        )
        assertTrue(
            networkDns.ips.isEmpty(),
            "Network DNS should have empty IPs list"
        )
    }
}
