package org.opendc.experiments.timo.input

/**
 * An [InputConverter] that represents the identity function.
 */
object DefaultInputConverter : InputConverter {
    override fun fromString(value: String, target: Class<*>): Any? = value
}
