/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.lispflowmapping.implementation.inventory;

import java.net.InetAddress;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.opendaylight.controller.sal.core.Property;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class IPAddressNodeProperty extends Property implements Cloneable {
    private static final long serialVersionUID = 1L;
    @XmlElement(name="value")
    private final InetAddress address;
    public static final String name = "IPAddress";

    /*
     * Private constructor used for JAXB mapping
     */
    private IPAddressNodeProperty() {
        super(name);
        this.address = null;
    }

    public IPAddressNodeProperty(InetAddress address) {
        super(name);
        this.address = address;
    }

    @Override
    public String getStringValue() {
        if (address == null) return null;
        return this.address.getHostAddress();
    }

    @Override
    public Property clone() {
        return new IPAddressNodeProperty(this.address);
    }

    public InetAddress getAddress() {
        return address;
    }
}
