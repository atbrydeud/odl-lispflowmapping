package org.opendaylight.lispflowmapping.implementation.authentication;

import org.opendaylight.lispflowmapping.type.lisp.MapNotify;
import org.opendaylight.lispflowmapping.type.lisp.MapRegister;

public class LispNoAuthentication implements ILispAuthentication {

    private static final LispNoAuthentication INSTANCE = new LispNoAuthentication();

    private static byte[] authenticationData;

    public static LispNoAuthentication getInstance() {
        return INSTANCE;
    }

    private LispNoAuthentication() {
        authenticationData = new byte[0];
    }

    public int getAuthenticationLength() {
        return 0;
    }

    public byte[] getAuthenticationData(MapNotify mapNotify, String key) {
        return authenticationData;
    }

    public boolean validate(MapRegister mapRegister, String key) {
        return true;
    }

}