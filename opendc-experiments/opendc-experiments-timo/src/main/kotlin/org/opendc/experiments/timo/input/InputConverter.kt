package org.opendc.experiments.timo.input

import java.io.Serializable

/**
 * A converter for input values.
 */
interface InputConverter : Serializable {
    /**
     * Transform the given string [value] into a value of the specified type.
     *
     * @param value The value to convert into the specified target type.
     * @param target The target type to convert to.
     * @return The string value converted to an instance of type [target].
     */
    fun fromString(value: String, target: Class<*>): Any?

    /**
     * Transform the given [value] into a string representation. This may be useful if the [toString] method of the
     * object does not return an appropriate value.
     *
     * @param value The value to convert.
     * @return The string representation of the value.
     */
    fun toString(value: Any?): String = value.toString()
}
