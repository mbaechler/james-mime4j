/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mime4j.stream;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

/**
 * The basic immutable MIME field.
 */
public class RawFieldParser {

    static final int COLON   = ':';
    static final int SPACE   = 0x20;
    static final int TAB     = 0x09;
    static final int CR      = 0x0d;
    static final int LF      = 0x0a;
    
    public static final RawFieldParser DEFAULT = new RawFieldParser(); 

    public RawField parseField(final ByteSequence raw) throws MimeException {
        if (raw == null) {
            return null;
        }
        int idx = indexOf(raw, COLON);
        if (idx == -1) {
            throw new MimeException("Invalid MIME field: no name/value separator found: " +
            		raw.toString());
        }
        String name = copyTrimmed(raw, 0, idx);
        return new RawField(raw, idx, name, null);
    }

    public RawBody parseRawBody(final RawField field) {
        ByteSequence buf = field.getRaw();
        int pos = field.getDelimiterIdx() + 1; 
        if (buf == null) {
            String body = field.getBody();
            if (body == null) {
                return new RawBody("", null);
            }
            buf = ContentUtil.encode(body);
            pos = 0;
        }
        ParserCursor cursor = new ParserCursor(pos, buf.length());
        return parseRawBody(buf, cursor);
    }
    
    static final int[] DELIMS = { ';' };

    RawBody parseRawBody(final ByteSequence buf, final ParserCursor cursor) {
        int pos = cursor.getPos();
        int indexFrom = pos;
        int indexTo = cursor.getUpperBound();
        while (pos < indexTo) {
            int ch = buf.byteAt(pos);
            if (isOneOf(ch, DELIMS)) {
                break;
            }
            pos++;
        }
        String value = copyTrimmed(buf, indexFrom, pos);
        if (pos == indexTo) {
            cursor.updatePos(pos);
            return new RawBody(value, null);
        }
        cursor.updatePos(pos + 1);
        List<NameValuePair> params = parseParameters(buf, cursor);
        return new RawBody(value, params);
    }
    
    List<NameValuePair> parseParameters(final ByteSequence buf, final ParserCursor cursor) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        int pos = cursor.getPos();
        int indexTo = cursor.getUpperBound();

        while (pos < indexTo) {
            int ch = buf.byteAt(pos);
            if (isWhitespace(ch)) {
                pos++;
            } else {
                break;
            }
        }
        cursor.updatePos(pos);
        if (cursor.atEnd()) {
            return params;
        }

        while (!cursor.atEnd()) {
            NameValuePair param = parseParameter(buf, cursor, DELIMS);
            params.add(param);
        }
        return params;
    }

    NameValuePair parseParameter(final ByteSequence buf, final ParserCursor cursor) {
        return parseParameter(buf, cursor, DELIMS);
    }
    
    NameValuePair parseParameter(final ByteSequence buf, final ParserCursor cursor, final int[] delimiters) {
        boolean terminated = false;

        int pos = cursor.getPos();
        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();

        // Find name
        String name = null;
        while (pos < indexTo) {
            int ch = buf.byteAt(pos);
            if (ch == '=') {
                break;
            }
            if (isOneOf(ch, delimiters)) {
                terminated = true;
                break;
            }
            pos++;
        }

        if (pos == indexTo) {
            terminated = true;
            name = copyTrimmed(buf, indexFrom, indexTo);
        } else {
            name = copyTrimmed(buf, indexFrom, pos);
            pos++;
        }

        if (terminated) {
            cursor.updatePos(pos);
            return new NameValuePair(name, null, false);
        }

        // Find value
        String value = null;
        int i1 = pos;

        boolean qouted = false;
        boolean escaped = false;
        while (pos < indexTo) {
            int ch = buf.byteAt(pos);
            if (ch == '"' && !escaped) {
                qouted = !qouted;
            }
            if (!qouted && !escaped && isOneOf(ch, delimiters)) {
                terminated = true;
                break;
            }
            if (escaped) {
                escaped = false;
            } else {
                escaped = qouted && ch == '\\';
            }
            pos++;
        }

        int i2 = pos;
        // Trim leading white spaces
        while (i1 < i2 && (isWhitespace(buf.byteAt(i1)))) {
            i1++;
        }
        // Trim trailing white spaces
        while ((i2 > i1) && (isWhitespace(buf.byteAt(i2 - 1)))) {
            i2--;
        }
        boolean quoted = false;
        // Strip away quotes if necessary
        if (((i2 - i1) >= 2)
            && (buf.byteAt(i1) == '"')
            && (buf.byteAt(i2 - 1) == '"')) {
            quoted = true;
            i1++;
            i2--;
        }
        if (quoted) {
            value = copyEscaped(buf, i1, i2);
        } else {
            value = copy(buf, i1, i2);
        }
        if (terminated) {
            pos++;
        }
        cursor.updatePos(pos);
        return new NameValuePair(name, value, quoted);
    }
    
    static int indexOf(final ByteSequence buf, int b) {
        for (int i = 0; i < buf.length(); i++) {
            if (buf.byteAt(i) == b) {
                return i;
            }
        }
        return -1;
    }
    
    static boolean isOneOf(final int ch, final int[] chs) {
        if (chs != null) {
            for (int i = 0; i < chs.length; i++) {
                if (ch == chs[i]) {
                    return true;
                }
            }
        }
        return false;
    }
    
    static boolean isWhitespace(int i) {
        return i == SPACE || i == TAB || i == CR || i == LF;
    }
    
    static String copy(final ByteSequence buf, int beginIndex, int endIndex) {
        StringBuilder strbuf = new StringBuilder(endIndex - beginIndex);
        for (int i = beginIndex; i < endIndex; i++) {
            strbuf.append((char) (buf.byteAt(i) & 0xff));
        }
        return strbuf.toString();
    }

    static String copyTrimmed(final ByteSequence buf, int beginIndex, int endIndex) {
        while (beginIndex < endIndex && isWhitespace(buf.byteAt(beginIndex))) {
            beginIndex++;
        }
        while (endIndex > beginIndex && isWhitespace(buf.byteAt(endIndex - 1))) {
            endIndex--;
        }
        return copy(buf, beginIndex, endIndex);
    }

    static String copyEscaped(final ByteSequence buf, int beginIndex, int endIndex) {
        StringBuilder strbuf  = new StringBuilder(endIndex - beginIndex);
        boolean escaped = false;
        for (int i = beginIndex; i < endIndex; i++) {
            char ch = (char) (buf.byteAt(i) & 0xff);
            if (escaped) {
                if (ch != '\"' && ch != '\\') {
                    strbuf.append('\\');
                }
                strbuf.append(ch);
                escaped = false;
            } else {
                if (ch == '\\') {
                    escaped = true;
                } else {
                    strbuf.append(ch);
                }
            }
        }
        return strbuf.toString();
    }

}
