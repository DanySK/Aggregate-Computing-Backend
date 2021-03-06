package server

import devices.interfaces.Device
import java.lang.Exception

/**
 * Manager for the actually present Devices
 */
class DeviceManager {
    private val devices: MutableList<Device> = mutableListOf()
    private val neighbours: MutableMap<Device, Set<Device>> = mutableMapOf()

    private var finalized: Boolean = false
    /**
     * Generates a new, unused Device ID
     */
    private fun generateID(): Int {
        var generated = devices.size
        while (devices.any { it.id == generated })
            generated++
        return generated
    }

    fun reset() {
        devices.clear()
        neighbours.clear()
        finalized = false
    }

    /**
     * Cuts the possibility to add new devices and sets the neighbours of each Device based on the chosen Topology
     */
    fun finalize(topology: Topology) {
        if (finalized)
            throw Exception("Already finalized")

        if (devices.size > 1) {
            when (topology) {
                Topology.Line ->
                    devices.forEachIndexed { index, d ->
                        neighbours[d] = when (index) {
                            0 -> setOf(devices[index + 1])
                            devices.size - 1 -> setOf(devices[index - 1])
                            else -> setOf(devices[index - 1], devices[index + 1])
                        }
                    }
                Topology.Ring ->
                    devices.forEachIndexed { index, d ->
                        neighbours[d] =
                            setOf(
                                devices[(index - 1 + devices.size) % devices.size],
                                devices[(index + 1) % devices.size]
                            )
                    }
                Topology.FullyConnected ->
                    devices.forEach {
                        neighbours[it] = devices.minus(it).toSet()
                    }
            }
        }
        finalized = true
    }

    fun createDevice(device: (Int)-> Device): Device = device(generateID())

    fun createAndAddDevice(device: (Int)-> Device): Device {
        val created = createDevice(device)
        this += created
        return created
    }

    operator fun plusAssign(device: Device) {
        if (finalized)
            throw Exception("Cannot add a new Device after finalization")
        else if (!devices.contains(device))
            devices += device
    }

    fun getDevices(): List<Device> = devices.toList()

    fun getNeighbours(deviceID: Int, selfIncluded: Boolean = false): Set<Device> {
        val device = devices.firstOrNull{it.id == deviceID}
        return if (device != null)
            getNeighbours(device, selfIncluded)
        else
            emptySet()
    }
    fun getNeighbours(device: Device, selfIncluded: Boolean = false): Set<Device> {
        if (!finalized)
            throw Exception("Cannot get neighbours before finalization")

        return if (selfIncluded)
            neighbours.getOrDefault(device, emptySet()) + device
        else
            neighbours.getOrDefault(device, emptySet())
    }

    /**
     * Swaps a device with another one, keeping the same neighbours
     */
    fun replace(toReplace: Device, replacement: Device) {
        //inherit the status
        replacement.status = toReplace.status

        //inherit the neighbours
        val oldNeighbours = getNeighbours(toReplace)
        neighbours.remove(toReplace)
        neighbours[replacement] = oldNeighbours

        //neighbours.replace(replacement, getNeighbours(toReplace))

        //update devices with toReplace as neighbour
        neighbours.filter { it.value.contains(toReplace) }.forEach {
            neighbours[it.key] = it.value - toReplace + replacement
        }

        devices -= toReplace
        devices += replacement
    }

    /**
     * Prints all the relationships
     */
    fun printNeighbours() {
        if (!finalized)
            throw Exception("Cannot print neighbours before finalization")

        neighbours.flatMap { entry ->
            entry.value.map { setOf(entry.key, it) }
        }.toSet()
            .map { it.joinToString(" <-> ") }
            .forEach(::println)
    }
}

enum class Topology {
    /**
     * Each Device will have a max of 2 neighbours, the ones that joined immediately before and after it
     */
    Line,
    /**
     * Just like Line, but the first will have as neighbour the last one and vice-versa
     */
    Ring,
    /**
     * Everyone is connected with everyone else
     */
    FullyConnected
}