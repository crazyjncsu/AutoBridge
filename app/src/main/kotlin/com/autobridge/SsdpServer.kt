package com.autobridge

import android.util.Log
import java.io.Closeable
import java.net.*
import java.util.*

class SsdpServer(val listener: Listener) {
    private var closeableToStop: Closeable? = null
    private var isStopping = false

    private var thread = Thread(Runnable {
        while (!this@SsdpServer.isStopping) {
            tryLog {
                val networkInterface = getActiveNetworkInterface()

                MulticastSocket(1900).use { multicastSocket ->
                    this@SsdpServer.closeableToStop = multicastSocket

                    multicastSocket.loopbackMode = true

                    multicastSocket.joinGroup(
                            InetSocketAddress("239.255.255.250", 1900),
                            networkInterface
                    )

                    DatagramSocket(null).use { unicastSocket ->
                        unicastSocket.reuseAddress = true

                        val bindAddress = networkInterface.inetAddresses.asSequence().filter { isAddressValid(it) }.first()
                        unicastSocket.bind(InetSocketAddress(bindAddress, 1900))

                        while (!this@SsdpServer.isStopping) {
                            val buffer = ByteArray(1024)
                            val packet = DatagramPacket(buffer, buffer.count())
                            multicastSocket.receive(packet)

                            Log.v(TAG, "Received SSDP multicast packet...")

                            val packetContentString = String(packet.data)
                            val scanner = Scanner(packetContentString)

                            if (scanner.nextLine() == "M-SEARCH * HTTP/1.1") {
                                Log.v(TAG, "SSDP multicast packet is a search...")

                                while (scanner.hasNext()) {
                                    val lineParts = scanner.nextLine().split(':', limit = 2)

                                    if (lineParts[0] == "ST") {
                                        this@SsdpServer.listener.onSearch(lineParts[1].trim()).forEach {
                                            val headers = mapOf(
                                                    "CACHE-CONTROL" to "max-age = 3600",
                                                    "EXT" to "",
                                                    "LOCATION" to "http:/$bindAddress:8080/xml/device_description.xml", // address.toString has a prefix of slash
                                                    "SERVER" to "Android, UPnP/1.0, AutoBridge",
                                                    "ST" to it.st,
                                                    "USN" to "uuid:${it.usn}::${it.st}"
                                            )

                                            val responseString = arrayOf("HTTP/1.1 200 OK")
                                                    .plus(headers.entries.map { it.key + ": " + it.value })
                                                    .joinToString("\r\n") + "\r\n\r\n"

                                            Log.v(TAG, "Sending SSDP response (st: ${it.st}, usn: ${it.usn}) to ${packet.address}:${packet.port}")

                                            val responsePacket = responseString.toByteArray().let { DatagramPacket(it, it.count(), packet.address, packet.port) }
                                            unicastSocket.send(responsePacket)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    })

    fun start() {
        this.isStopping = false
        this.thread.start()
    }

    fun stop() {
        this.isStopping = true
        this.closeableToStop?.close()
        this.thread.join()
    }

    interface Listener {
        fun onSearch(st: String): List<SearchResponse>

        data class SearchResponse(val st: String, val usn: UUID)
    }
}
