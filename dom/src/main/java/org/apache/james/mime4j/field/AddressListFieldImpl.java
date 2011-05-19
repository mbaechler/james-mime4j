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

package org.apache.james.mime4j.field;

import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.field.address.AddressBuilder;
import org.apache.james.mime4j.field.address.ParseException;
import org.apache.james.mime4j.stream.FieldParser;
import org.apache.james.mime4j.util.ByteSequence;

/**
 * Address list field such as <code>To</code> or <code>Reply-To</code>.
 */
public class AddressListFieldImpl extends AbstractField implements org.apache.james.mime4j.dom.field.AddressListField {

    private boolean parsed = false;

    private AddressList addressList;
    private ParseException parseException;

    AddressListFieldImpl(String name, String body, ByteSequence raw, DecodeMonitor monitor) {
        super(name, body, raw, monitor);
    }

    /**
     * @see org.apache.james.mime4j.dom.field.AddressListField#getAddressList()
     */
    public AddressList getAddressList() {
        if (!parsed)
            parse();

        return addressList;
    }

    /**
     * @see org.apache.james.mime4j.dom.field.AddressListField#getParseException()
     */
    @Override
    public ParseException getParseException() {
        if (!parsed)
            parse();

        return parseException;
    }

    private void parse() {
        String body = getBody();

        try {
            addressList = AddressBuilder.DEFAULT.parseAddressList(body, monitor);
        } catch (ParseException e) {
            parseException = e;
        }

        parsed = true;
    }

    public static final FieldParser<AddressListField> PARSER = new FieldParser<AddressListField>() {
        public AddressListField parse(final String name, final String body,
                final ByteSequence raw, DecodeMonitor monitor) {
            return new AddressListFieldImpl(name, body, raw, monitor);
        }
    };
}
