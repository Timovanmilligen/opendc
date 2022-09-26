package org.opendc.compute.service.scheduler

import java.rmi.server.UID
import java.util.*
import javax.sound.sampled.Port

/**
 * A portfolio of scheduling policies
 */
public class Portfolio {
    public val policies : MutableList<ComputeScheduler> = mutableListOf()

    public fun addEntry(entry: ComputeScheduler){
        policies.add(entry)
    }
    public fun getSize() : Int{
        return policies.size
    }
}

