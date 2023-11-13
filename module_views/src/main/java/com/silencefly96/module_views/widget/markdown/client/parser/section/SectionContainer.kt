package com.silencefly96.module_views.widget.markdown.client.parser.section

/**
 * 储存section的容器，通过迭代器模式访问
 */
class SectionContainer: Container<Section> {

    private val sections = ArrayList<Section>()

    override fun getIterator(): Iterator<Section> {
        return SectionIterator()
    }

    override fun add(element: Section) {
        sections.add(element)
    }

    inner class SectionIterator: Iterator<Section> {
        // 迭代器指针
        private var cursor = 0

        override fun hasNext(): Boolean {
            return cursor < sections.size
        }

        override fun next(): Section {
            if (cursor >= sections.size) {
                throw NoSuchElementException()
            }
            return sections[cursor++]
        }
    }
}

interface Container<T> {
    fun getIterator(): Iterator<T>
    fun add(element: T)
}