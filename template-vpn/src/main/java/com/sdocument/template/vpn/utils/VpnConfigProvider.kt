package com.sdocument.template.vpn.utils

/**
 * To make these hardcoded VPN configs actually work, you need to provide real WireGuard configuration details.
 * For each server, you must fill in:
 *
 * 1. [Interface] Section (Client details):
 *    - PrivateKey: Your client's private key (Base64 encoded string).
 *    - Address: The internal IP address assigned to your client (e.g., "10.0.0.2/32").
 *    - DNS: (Optional) The DNS server to use (e.g., "1.1.1.1").
 *
 * 2. [Peer] Section (Server details):
 *    - PublicKey: The server's public key (Base64 encoded string).
 *    - Endpoint: The server's public IP address and port (e.g., "198.51.100.1:51820").
 *    - AllowedIPs: The IP ranges to route through the VPN. Use "0.0.0.0/0, ::/0" for all traffic.
 */
object VpnConfigProvider {

    data class VpnServer(
        val name: String,
        val ping: String,
        val config: String
    )

    // REPLACE THESE PLACEHOLDERS WITH REAL WIREGUARD CONFIGURATIONS
    val servers = listOf(
        VpnServer(
            name = "Russia, Moscow",
            ping = "15 ms",
            config = """
                [Interface]
                PrivateKey = REPLACE_WITH_CLIENT_PRIVATE_KEY_1
                Address = 10.0.0.2/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = REPLACE_WITH_SERVER_PUBLIC_KEY_1
                AllowedIPs = 0.0.0.0/0
                Endpoint = 198.51.100.1:51820
            """.trimIndent()
        ),
        VpnServer(
            name = "Germany, Frankfurt",
            ping = "45 ms",
            config = """
                [Interface]
                PrivateKey = REPLACE_WITH_CLIENT_PRIVATE_KEY_2
                Address = 10.0.0.3/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = REPLACE_WITH_SERVER_PUBLIC_KEY_2
                AllowedIPs = 0.0.0.0/0
                Endpoint = 198.51.100.2:51820
            """.trimIndent()
        ),
        VpnServer(
            name = "Netherlands, Amsterdam",
            ping = "50 ms",
            config = """
                [Interface]
                PrivateKey = REPLACE_WITH_CLIENT_PRIVATE_KEY_3
                Address = 10.0.0.4/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = REPLACE_WITH_SERVER_PUBLIC_KEY_3
                AllowedIPs = 0.0.0.0/0
                Endpoint = 198.51.100.3:51820
            """.trimIndent()
        ),
        VpnServer(
            name = "USA, New York",
            ping = "120 ms",
            config = """
                [Interface]
                PrivateKey = REPLACE_WITH_CLIENT_PRIVATE_KEY_4
                Address = 10.0.0.5/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = REPLACE_WITH_SERVER_PUBLIC_KEY_4
                AllowedIPs = 0.0.0.0/0
                Endpoint = 198.51.100.4:51820
            """.trimIndent()
        ),
        VpnServer(
            name = "UK, London",
            ping = "55 ms",
            config = """
                [Interface]
                PrivateKey = REPLACE_WITH_CLIENT_PRIVATE_KEY_5
                Address = 10.0.0.6/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = REPLACE_WITH_SERVER_PUBLIC_KEY_5
                AllowedIPs = 0.0.0.0/0
                Endpoint = 198.51.100.5:51820
            """.trimIndent()
        )
    )

    fun getConfigForServer(name: String): String? {
        return servers.find { it.name == name }?.config
    }
}
