package com.nam.novelreader.extension.runtime.api

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import org.jsoup.select.Elements
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptableObject

fun interface Consumer {
    fun accept(obj: Any?)
}

fun interface MapFunction {
    fun accept(obj: Any?): Any?
}

class JSList {
    @JvmField
    var length: Int = 0
    private val list = ArrayList<Any?>()

    fun add(obj: Any?): Boolean {
        val added = list.add(obj)
        length = list.size
        return added
    }

    fun addAll(collection: Collection<Any?>): Boolean {
        val added = list.addAll(collection)
        length = list.size
        return added
    }

    fun forEach(consumer: Consumer) {
        for (i in 0 until list.size) {
            consumer.accept(list[i])
        }
    }

    fun get(i: Int): Any? {
        return list.getOrNull(i)
    }

    fun join(obj: Any?): String {
        if (list.isEmpty()) return ""
        val delimiter = obj?.toString() ?: ""
        return list.joinToString(delimiter) { it?.toString() ?: "" }
    }

    fun map(mapFunction: MapFunction): JSList {
        val jsList = JSList()
        for (i in 0 until list.size) {
            jsList.add(mapFunction.accept(list[i]))
        }
        return jsList
    }

    fun add(i: Int, obj: Any?) {
        list.add(i, obj)
        length = list.size
    }
}

class JSElement(private val element: Element?) {
    fun getJsoupElement(): Element? = element

    private fun wrapNonNull(str: String?): String {
        return str ?: ""
    }

    fun attr(obj: Any?): String {
        if (element == null) return ""
        return wrapNonNull(element.attr(obj?.toString() ?: ""))
    }

    fun html(): String {
        if (element == null) return ""
        return wrapNonNull(element.html())
    }

    fun outerHtml(): String {
        if (element == null) return ""
        return wrapNonNull(element.outerHtml())
    }

    fun remove() {
        element?.remove()
    }

    fun select(obj: Any?): JSElements {
        if (element == null) return JSElements(Elements())
        return try {
            JSElements(element.select(obj?.toString() ?: ""))
        } catch (e: Exception) {
            JSElements(Elements())
        }
    }

    fun tagName(str: String): JSElement {
        if (element == null) return JSElement(null)
        element.tagName(str)
        return this
    }

    fun text(): String {
        if (element == null) return ""
        return wrapNonNull(element.text())
    }

    override fun toString(): String {
        return element?.toString() ?: ""
    }

    fun tagName(): String {
        return element?.tagName() ?: ""
    }
}

class JSElements(private val elements: Elements?) {
    @JvmField
    var length: Int = elements?.size ?: 0

    private fun wrapNonNull(str: String?): String {
        return str ?: ""
    }

    fun attr(obj: Any?): String {
        if (elements == null) return ""
        return wrapNonNull(elements.attr(obj?.toString() ?: ""))
    }

    fun first(): JSElement {
        if (elements == null || elements.isEmpty()) return JSElement(null)
        return JSElement(elements.first())
    }

    fun forEach(consumer: Consumer) {
        if (elements != null) {
            for (i in 0 until elements.size) {
                consumer.accept(JSElement(elements[i]))
            }
        }
    }

    fun get(i: Int): JSElement {
        if (elements != null && i >= 0 && i < elements.size) {
            return JSElement(elements[i])
        }
        return JSElement(null)
    }

    fun html(): String {
        if (elements == null) return ""
        return wrapNonNull(elements.html())
    }

    fun isEmpty(): Boolean {
        return length == 0
    }

    fun last(): JSElement {
        if (elements == null || elements.isEmpty()) return JSElement(null)
        return JSElement(elements.last())
    }

    fun map(mapFunction: MapFunction): JSList {
        val jsList = JSList()
        if (elements != null) {
            for (i in 0 until elements.size) {
                jsList.add(mapFunction.accept(JSElement(elements[i])))
            }
        }
        return jsList
    }

    fun outerHtml(): String {
        if (elements == null) return ""
        return wrapNonNull(elements.outerHtml())
    }

    fun remove() {
        elements?.remove()
    }

    fun select(str: String): JSElements {
        if (elements == null) return JSElements(Elements())
        return try {
            JSElements(elements.select(str))
        } catch (e: Exception) {
            JSElements(Elements())
        }
    }

    fun size(): Int {
        return length
    }

    fun tagName(str: String): JSElements {
        if (elements == null) return JSElements(Elements())
        elements.tagName(str)
        return this
    }

    fun text(): String {
        if (elements == null) return ""
        return wrapNonNull(elements.text())
    }

    override fun toString(): String {
        return elements?.toString() ?: ""
    }
}

class JSDocument(private val document: Document?) {
    fun getJsoupDocument(): Document? = document

    private fun wrapNonNull(str: String?): String {
        return str ?: ""
    }

    fun attr(obj: Any?): String {
        if (document == null) return ""
        return wrapNonNull(document.attr(obj?.toString() ?: ""))
    }

    fun html(): String {
        if (document == null) return ""
        return wrapNonNull(document.html())
    }

    fun remove() {
        document?.remove()
    }

    fun select(obj: Any?): JSElements {
        return try {
            JSElements(document?.select(obj?.toString() ?: ""))
        } catch (e: Exception) {
            JSElements(Elements())
        }
    }

    fun selectXpath(obj: Any?): JSElements {
        return try {
            JSElements(Elements(document?.selectXpath(obj?.toString() ?: "") ?: emptyList()))
        } catch (e: Exception) {
            JSElements(Elements())
        }
    }

    fun tagName(str: String): JSElement {
        if (document == null) return JSElement(null)
        document.tagName(str)
        return JSElement(document)
    }

    fun text(): String {
        if (document == null) return ""
        return wrapNonNull(document.text())
    }

    override fun toString(): String {
        return document?.toString() ?: ""
    }

    fun tagName(): String {
        return document?.tagName() ?: ""
    }
}

class JSHtml {
    fun clean(obj: Any?, obj2: Any?): String {
        val html = obj?.toString() ?: ""
        val safelist = Safelist.none()
        if (obj2 is NativeArray) {
            for (i in 0 until obj2.size.toInt()) {
                safelist.addTags(obj2.get(i).toString())
            }
        } else {
            val tags = obj2?.toString() ?: ""
            if (tags.isNotBlank()) {
                safelist.addTags(*tags.split(",").map { it.trim() }.toTypedArray())
            }
        }
        return Jsoup.clean(html, safelist)
    }

    fun parse(obj: Any?): JSDocument {
        val html = obj?.toString() ?: ""
        return JSDocument(Jsoup.parse(html))
    }
}

object JSHtmlBridge {
    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val jsHtml = JSHtml()
        val wrapped = RhinoContext.javaToJS(jsHtml, scope)
        ScriptableObject.putProperty(scope, "Html", wrapped)
    }
}
