
package io.github.workoss.plugin

import java.util.regex.Pattern

interface Shlex {
    companion object {
        /**
         * Escape a string for use in a shell command.
         *
         * @param s The string to escape.
         * @return The escaped string.
         * @see (https://docs.python.org/3/library/shlex.html.shlex.quote)
         */
        fun quote(s: String): String {
            if (s.isEmpty()) return "''"
            val unsafe = Pattern.compile("[^\\w@%+=:,./-]")
            return if (unsafe.matcher(s).find()) "'" + s.replace("'", "'\"'\"'") + "'"
            else s
        }

        fun quote(args: List<String?>): String {
            val sb = StringBuilder()
            for (arg in args) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(quote(arg.toString()))
            }
            return sb.toString()
        }
    }
}
