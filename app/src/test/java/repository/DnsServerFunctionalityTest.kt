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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for DNS server reachability and functionality.
 * Uses blokada.org as the test domain to verify DNS resolution works correctly.
 *
 * Run with: ./gradlew testDebugUnitTest -k "DnsServerFunctionality"
 */
@RunWith(Parameterized::class)
class DnsServerFunctionalityTest(
    private val dns: Dns
) {

    private val testDomain = "blokada.org"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .readTimeout(java.time.Duration.ofSeconds(10))
        .build()

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            return DnsDataSource.getDns().map { arrayOf(it) }
        }
    }

    @Test
    fun testDnsServerResolvesBlokakaOrg() {
        when {
            dns.isDnsOverHttps() -> testDoHResolution()
            else -> testPlaintextDnsResolution()
        }
    }

    /**
     * Tests DNS over HTTPS (DoH) resolution by querying the DoH endpoint
     */
    private fun testDoHResolution() {
        assertNotNull(dns.name, "DoH server ${dns.id} missing hostname")
        assertNotNull(dns.path, "DoH server ${dns.id} missing path")

        val request = Request.Builder()
            .url("https://${dns.name}/${dns.path}?name=$testDomain&type=A")
            .addHeader("Accept", "application/dns-message")
            .build()

        val response = httpClient.newCall(request).execute()

        assertTrue(
            response.isSuccessful,
            "DoH resolution failed for ${dns.label}: HTTP ${response.code}"
        )

        val body = response.body?.string()
        assertNotNull(body, "DoH response body is empty for ${dns.label}")
        assertTrue(
            body.isNotEmpty(),
            "DoH response is empty for ${dns.label}"
        )
    }

    /**
     * Tests plaintext DNS (UDP/53) resolution
     */
    private fun testPlaintextDnsResolution() {
        val ips = dns.ips.filter { it.isIpv4() } // Test IPv4 only for simplicity
        assertTrue(ips.isNotEmpty(), "DNS server ${dns.id} has no IPv4 addresses")

        var lastError: Exception? = null

        for (ip in ips) {
            try {
                val result = queryDnsServer(ip, testDomain)
                assertTrue(
                    result,
                    "DNS server ${dns.label} ($ip) returned no results for $testDomain"
                )
                return // Success on first reachable server
            } catch (e: Exception) {
                lastError = e
                // Continue to next IP
            }
        }

        throw AssertionError(
            "DNS server ${dns.label} failed all IPs: ${lastError?.message}",
            lastError
        )
    }

    /**
     * Performs a simple DNS A-record query using UDP/53
     */
    private fun queryDnsServer(ip: String, domain: String): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 5000

                // Simple DNS query for A record of blokada.org
                val query = buildSimpleDnsQuery(domain)
                val packet = DatagramPacket(
                    query,
                    query.size,
                    InetAddress.getByName(ip),
                    53
                )

                socket.send(packet)

                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                // Check if response has answers (byte position 6-7)
                val answerCount = (responseBuffer[6].toInt() shl 8) or (responseBuffer[7].toInt() and 0xFF)
                answerCount > 0
            }
        } catch (e: Exception) {
            throw Exception("Failed to query $ip for $domain", e)
        }
    }

    /**
     * Builds a minimal DNS query packet for A records
     */
    private fun buildSimpleDnsQuery(domain: String): ByteArray {
        val query = mutableListOf<Byte>()

        // DNS header: ID (2 bytes)
        query.add(0x12)
        query.add(0x34)

        // Flags: standard query (1 byte 0x00, 1 byte 0x00)
        query.add(0x00)
        query.add(0x00)

        // Question count: 1
        query.add(0x00)
        query.add(0x01)

        // Answer, Authority, Additional: 0
        query.add(0x00)
        query.add(0x00)
        query.add(0x00)
        query.add(0x00)

        // Question: domain name (labels separated by length bytes)
        for (label in domain.split(".")) {
            query.add(label.length.toByte())
            for (char in label) {
                query.add(char.code.toByte())
            }
        }
        query.add(0x00) // Root label

        // Query type: A (0x0001)
        query.add(0x00)
        query.add(0x01)

        // Query class: IN (0x0001)
        query.add(0x00)
        query.add(0x01)

        return query.toByteArray()
    }
}
