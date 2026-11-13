package dev.pulse.app.data.importer

import java.io.Reader

/**
 * Очень простой и быстрый «читатель» файла export.xml из Apple Health.
 *
 * Обычные XML-библиотеки спотыкаются об этот файл: он бывает размером в гигабайты,
 * а в его заголовке Apple иногда пишет некорректное описание формата. Поэтому мы не разбираем
 * XML целиком, а просто идём по файлу и вытаскиваем нужные теги (`<Record …>`, `<Workout …>`)
 * вместе с их атрибутами. Всё остальное пропускается.
 *
 * Класс не зависит от Android и проверяется обычными тестами.
 */
class AppleHealthXmlScanner(
    private val reader: Reader,
    private val wantedTags: Set<String>,
) {
    private val buffer = CharArray(1 shl 16)
    private var position = 0
    private var length = 0
    private val text = StringBuilder(256)

    /** Имя найденного тега (после успешного [next]). */
    var tagName: String = ""
        private set

    /** Атрибуты найденного тега. Словарь переиспользуется — не храните ссылку на него. */
    val attributes: HashMap<String, String> = HashMap()

    private fun read(): Int {
        if (position >= length) {
            length = reader.read(buffer, 0, buffer.size)
            position = 0
            if (length <= 0) return -1
        }
        return buffer[position++].code
    }

    /** Переходит к следующему нужному тегу. false — файл закончился. */
    fun next(): Boolean {
        while (true) {
            var c = read()
            while (c != -1 && c != '<'.code) c = read()
            if (c == -1) return false
            c = read()
            if (c == -1) return false

            // Служебные конструкции (<!DOCTYPE, <!--, <?xml) и закрывающие теги — пропускаем.
            if (c == '!'.code || c == '?'.code || c == '/'.code) {
                while (c != -1 && c != '>'.code) c = read()
                if (c == -1) return false
                continue
            }

            text.setLength(0)
            while (c != -1 && !isSpace(c) && c != '>'.code && c != '/'.code) {
                text.append(c.toChar())
                c = read()
            }
            val name = text.toString()
            val wanted = name in wantedTags
            if (wanted) attributes.clear()

            // Атрибуты: имя="значение". Значение читаем до закрывающей кавычки,
            // поэтому символ '>' внутри значения нас не обманет.
            while (true) {
                while (isSpace(c)) c = read()
                if (c == -1) return false
                if (c == '>'.code) break
                if (c == '/'.code) {
                    c = read()
                    continue
                }
                text.setLength(0)
                while (c != -1 && c != '='.code && !isSpace(c) && c != '>'.code && c != '/'.code) {
                    text.append(c.toChar())
                    c = read()
                }
                val attributeName = text.toString()
                while (isSpace(c)) c = read()
                if (c != '='.code) continue
                c = read()
                while (isSpace(c)) c = read()
                val quote = c
                if (quote != '"'.code && quote != '\''.code) continue
                text.setLength(0)
                c = read()
                while (c != -1 && c != quote) {
                    if (wanted) text.append(c.toChar())
                    c = read()
                }
                if (c == -1) return false
                if (wanted) attributes[attributeName] = unescape(text)
                c = read()
            }

            if (wanted) {
                tagName = name
                return true
            }
        }
    }

    private fun isSpace(c: Int): Boolean = c == ' '.code || c == '\n'.code || c == '\t'.code || c == '\r'.code

    private fun unescape(raw: StringBuilder): String {
        if (raw.indexOf("&") < 0) return raw.toString()
        return raw.toString()
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }
}
