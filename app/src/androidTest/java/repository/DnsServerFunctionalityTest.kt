package repository

import model.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class DnsServerFunctionalityTest {

    private val testDomain = "blokada.org"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    @Test
    fun testDnsServersResolveBlokadaOrg() {
        val dnsList = DnsDataSource.getDns()
        var anySuccess = false
        val failures = mutableListOf<String>()

        for (dns in dnsList) {
            try {
                if (dns.isDnsOverHttps()) {
                    testDoHResolution(dns)
                } else {
                    testPlaintextDnsResolution(dns)
                }
                anySuccess = true
            } catch (e: Throwable) {
                failures += "Server ${dns.label} (${dns.id}) failed: ${e.message}"
            }
        }

        assertTrue(anySuccess, "No DNS servers succeeded. Failures:\n${failures.joinToString("\n")}")
    }

    private fun testDoHResolution(dns: Dns) {
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
        assertTrue(body.isNotEmpty(), "DoH response is empty for ${dns.label}")
    }

    private fun testPlaintextDnsResolution(dns: Dns) {
        val ips = dns.ips.filter { it.isIpv4() }
        assertTrue(ips.isNotEmpty(), "DNS server ${dns.id} has no IPv4 addresses")

        var lastError: Exception? = null

        for (ip in ips) {
            try {
                val result = queryDnsServer(ip, testDomain)
                assertTrue(result, "DNS server ${dns.label} ($ip) returned no results for $testDomain")
                return
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw AssertionError("DNS server ${dns.label} failed all IPs: ${lastError?.message}", lastError)
    }

    private fun queryDnsServer(ip: String, domain: String): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 5000
                val query = buildSimpleDnsQuery(domain)
                val packet = DatagramPacket(query, query.size, InetAddress.getByName(ip), 53)
                socket.send(packet)

                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                val answerCount = (responseBuffer[6].toInt() shl 8) or (responseBuffer[7].toInt() and 0xFF)
                answerCount > 0
            }
        } catch (e: Exception) {
            throw Exception("Failed to query $ip for $domain", e)
        }
    }

    private fun buildSimpleDnsQuery(domain: String): ByteArray {
        val query = mutableListOf<Byte>()

        query.add(0x12)
        query.add(0x34)

        query.add(0x00)
        query.add(0x00)

        query.add(0x00)
        query.add(0x01)

        query.add(0x00)
        query.add(0x00)
        query.add(0x00)
        query.add(0x00)

        for (label in domain.split(".")) {
            query.add(label.length.toByte())
            for (char in label) {
                query.add(char.code.toByte())
            }
        }
        query.add(0x00)

        query.add(0x00)
        query.add(0x01)

        query.add(0x00)
        query.add(0x01)

        return query.toByteArray()
    }
}
