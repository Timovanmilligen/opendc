package org.opendc.experiments.timo.input

/**
 * An input of an experiment.
 */
abstract class Input {
    /**
     * The name of this input.
     */
    abstract val name: String

    /**
     * The type of this input's value.
     */
    abstract val type: Class<*>

    /**
     * The unmaterialized values of this input.
     */
    abstract val values: Collection<String>

    /**
     * The [InputConverter] of this input.
     */
    abstract val converter: InputConverter

    /**
     * Obtain a collection of api of the specified [type] on the input.
     */
    abstract fun <T : Annotation> getAnnotations(type: Class<T>): Collection<T>

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean = other is Input && name == other.name

    companion object {
        /**
         * Create a manual [Input] with the given [name] and [type].
         *
         * @param name The name of the input.
         * @param type The type of the input.
         */
        fun of(name: String, type: Class<*>, values: Collection<String> = emptyList(), converter: InputConverter = DefaultInputConverter): Input {
            return object : Input() {
                override val name: String = name
                override val type: Class<*> = type
                override val values: Collection<String> = values
                override val converter: InputConverter = converter
                override fun <T : Annotation> getAnnotations(type: Class<T>): Collection<T> = emptyList()
            }
        }
    }
}
