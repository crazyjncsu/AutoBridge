package com.autobridge.android

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class NetworkDiscoverer {
    fun startDiscovery() {
        val networkInterface = getActiveNetworkInterface()

        val executor = Executors.newFixedThreadPool(4)

        val subnetAddresses = networkInterface.interfaceAddresses.flatMap<InterfaceAddress, Inet4Address> {
            val subnetBitCount = 32 - it.networkPrefixLength
            val subnetBaseAddress = it.address.address.map { it.toInt() }.reduce { acc, byte -> acc shl 8 or byte } and 0xFFFFFFFF.toInt() shl subnetBitCount
            (0..(1 shl subnetBitCount)).map {
                InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(subnetBaseAddress or it).array()) as Inet4Address
            }
        }

        subnetAddresses.forEach {
            executor.execute(object : Runnable {
                override fun run() {
                    val process = Runtime.getRuntime().exec("ping -c 1 " + it);
                    val returnCode = process.waitFor()
                }
            })
        }


        //Runtime.getRuntime().exec("ping -c 1 " + currentHost);
    }

    interface Listener {

    }
}