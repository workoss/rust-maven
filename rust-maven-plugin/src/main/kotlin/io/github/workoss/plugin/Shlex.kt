/*
 * Copyright 2024-2024 workoss (https://www.workoss.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            return if (unsafe.matcher(s).find()) "'" + s.replace("'", "'\"'\"'") + "'" else s
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
