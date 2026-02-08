@file:JvmName("SKaiNETNotebook")

package sk.ainet.app.notebook

/**
 * SKaiNET Kotlin Notebook Library
 * 
 * This library provides convenient access to SKaiNET functionality for Kotlin Notebooks.
 * It aggregates the core SKaiNET libraries for easy consumption in Jupyter environments.
 */

/**
 * Version information for the notebook library
 */
object NotebookInfo {
    val VERSION = GeneratedVersion.VERSION
    const val NAME = "SKaiNET Kotlin Notebook"
    
    /**
     * Print library information
     */
    fun info() {
        println("$NAME v$VERSION")
        println("Deep learning framework for Kotlin Notebooks")
    }
}