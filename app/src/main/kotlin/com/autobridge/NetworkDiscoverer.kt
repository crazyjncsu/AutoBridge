package com.autobridge

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NetworkDiscoverer(val listener: Listener) {
    fun startDiscovery() {
        asyncTryLog {
            val networkInterface = getActiveNetworkInterface()

            val executor = Executors.newFixedThreadPool(16)

            val subnetAddresses = networkInterface.interfaceAddresses
                    .filter { it.address is Inet4Address }
                    .flatMap<InterfaceAddress, Inet4Address> {
                        val subnetBitCount = 32 - it.networkPrefixLength
                        val subnetBaseAddress = (it.address.address.map { it.toUnsignedInt().toLong() }.reduce { acc, byte -> acc shl 8 or byte } and (0xFFFFFFFF shl subnetBitCount)).toInt()
                        (0 until (1 shl subnetBitCount)).map {
                            InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(subnetBaseAddress or it).array()) as Inet4Address
                        }
                    }

            val results = executor.invokeAll(subnetAddresses.map {
                Callable<Boolean> { Runtime.getRuntime().exec("ping -c 1 " + it.hostAddress).waitFor() == 0 }
            }).map { it.get() }

            val mappings = File("/proc/net/arp")
                    .readLines()
                    .drop(1) // header
                    .map { it.splitToSequence(' ').filter { it.isNotEmpty() }.toList() }
                    .map {
                        object {
                            val ipAddress = InetAddress.getByName(it[0])
                            val macAddress = it[3].splitToSequence(':').map { Integer.parseInt(it, 16).toByte() }.toList().toByteArray()
                        }
                    }

            mappings.forEach { this.listener.onMacAddressDiscovered(it.ipAddress, it.macAddress) }
        }
    }

    interface Listener {
        fun onMacAddressDiscovered(ipAddress: InetAddress, macAddress: ByteArray)
    }
}