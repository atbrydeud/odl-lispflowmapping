/*
 * Copyright (c) 2014 Contextream, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.lispflowmapping.implementation.lisp;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.opendaylight.lispflowmapping.implementation.authentication.LispAuthenticationUtil;
import org.opendaylight.lispflowmapping.implementation.dao.MappingServiceKeyUtil;
import org.opendaylight.lispflowmapping.implementation.util.MapNotifyBuilderHelper;
import org.opendaylight.lispflowmapping.interfaces.dao.ILispDAO;
import org.opendaylight.lispflowmapping.interfaces.dao.IMappingServiceKey;
import org.opendaylight.lispflowmapping.interfaces.dao.MappingEntry;
import org.opendaylight.lispflowmapping.interfaces.dao.MappingServiceRLOC;
import org.opendaylight.lispflowmapping.interfaces.dao.MappingServiceValue;
import org.opendaylight.lispflowmapping.interfaces.lisp.IMapNotifyHandler;
import org.opendaylight.lispflowmapping.interfaces.lisp.IMapServerAsync;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.MapNotify;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.MapRegister;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.eidtolocatorrecords.EidToLocatorRecord;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.lispaddress.LispAddressContainer;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.locatorrecords.LocatorRecord;
import org.opendaylight.yang.gen.v1.lispflowmapping.rev131031.mapnotifymessage.MapNotifyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapServer implements IMapServerAsync {
    private ILispDAO dao;
    private volatile boolean shouldAuthenticate;
    private volatile boolean shouldIterateMask;
    protected static final Logger logger = LoggerFactory.getLogger(MapServer.class);

    public MapServer(ILispDAO dao) {
        this(dao, true);
    }

    public MapServer(ILispDAO dao, boolean authenticate) {
        this(dao, authenticate, true);
    }

    public MapServer(ILispDAO dao, boolean authenticate, boolean iterateAuthenticationMask) {
        this.dao = dao;
        this.shouldAuthenticate = authenticate;
        this.shouldIterateMask = iterateAuthenticationMask;
    }

    public void handleMapRegister(MapRegister mapRegister, IMapNotifyHandler callback) {
        if (dao == null) {
            logger.warn("handleMapRegister called while dao is uninitialized");
        } else {
            boolean failed = false;
            String password = null;
            for (EidToLocatorRecord eidRecord : mapRegister.getEidToLocatorRecord()) {
                if (shouldAuthenticate) {
                    password = getPassword(eidRecord.getLispAddressContainer(), eidRecord.getMaskLength());
                    if (!LispAuthenticationUtil.validate(mapRegister, password)) {
                        logger.warn("Authentication failed");
                        failed = true;
                        break;
                    }
                }
                IMappingServiceKey key = MappingServiceKeyUtil.generateMappingServiceKey(eidRecord.getLispAddressContainer(),
                        eidRecord.getMaskLength());
                MappingServiceValue value = null;
                Map<String, ?> locators = dao.get(key);
                if (locators != null) {
                    value = (MappingServiceValue) locators.get("value");
                }
                if (value == null) {
                    value = new MappingServiceValue();
                    value.setRlocs(new ArrayList<MappingServiceRLOC>());
                }
                if (value.getRlocs() == null) {
                    value.setRlocs(new ArrayList<MappingServiceRLOC>());
                }

                MappingEntry<MappingServiceValue> entry = new MappingEntry<MappingServiceValue>("value", value);
                if (eidRecord.getLocatorRecord() != null) {
                    Map<LocatorRecord, MappingServiceRLOC> currentRLOCs = new HashMap<LocatorRecord, MappingServiceRLOC>();
                    for (MappingServiceRLOC msr : value.getRlocs()) {
                        currentRLOCs.put(msr.getRecord(), msr);
                    }

                    for (LocatorRecord locatorRecord : eidRecord.getLocatorRecord()) {
                        if (currentRLOCs.containsKey(locatorRecord)) {
                            currentRLOCs.get(locatorRecord).setAction(eidRecord.getAction()).setAuthoritative(eidRecord.isAuthoritative())
                                    .setRegisterdDate(new Date(System.currentTimeMillis())).setTtl(eidRecord.getRecordTtl());
                        } else {

                            value.getRlocs().add(
                                    new MappingServiceRLOC(locatorRecord, eidRecord.getRecordTtl(), eidRecord.getAction(), eidRecord
                                            .isAuthoritative()));
                        }
                    }
                }
                dao.put(key, entry);

            }
            if (!failed) {
                MapNotifyBuilder builder = new MapNotifyBuilder();
                if (BooleanUtils.isTrue(mapRegister.isWantMapNotify())) {
                    logger.trace("MapRegister wants MapNotify");
                    MapNotifyBuilderHelper.setFromMapRegister(builder, mapRegister);
                    if (shouldAuthenticate) {
                        builder.setAuthenticationData(LispAuthenticationUtil.createAuthenticationData(builder.build(), password));
                    }
                    callback.handleMapNotify(builder.build());
                }
            }
        }
    }

    private String getPassword(LispAddressContainer prefix, int maskLength) {
        while (maskLength >= 0) {
            IMappingServiceKey key = MappingServiceKeyUtil.generateMappingServiceKey(prefix, maskLength);
            Map<String, ?> daoMap = dao.get(key);
            if (daoMap != null) {
                MappingServiceValue value = (MappingServiceValue) daoMap.get("value");
                if (value != null && value.getKey() != null) {
                    return value.getKey();
                } else if (shouldIterateMask()) {
                    maskLength -= 1;
                } else {
                    return null;
                }
            } else {
                maskLength -= 1;

            }
        }
        return null;
    }

    public String getAuthenticationKey(LispAddressContainer address, int maskLen) {
        return getPassword(address, maskLen);
    }

    public boolean removeAuthenticationKey(LispAddressContainer address, int maskLen) {
        IMappingServiceKey key = MappingServiceKeyUtil.generateMappingServiceKey(address, maskLen);
        Map<String, ?> daoMap = dao.get(key);
        if (daoMap != null) {
            MappingServiceValue value = (MappingServiceValue) daoMap.get("value");
            if (value != null) {
                value.setKey(null);
                if (value.isEmpty()) {
                    dao.remove(key);
                } else {
                    dao.put(key, new MappingEntry<MappingServiceValue>("value", value));
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean addAuthenticationKey(LispAddressContainer address, int maskLen, String key) {
        IMappingServiceKey mappingServiceKey = MappingServiceKeyUtil.generateMappingServiceKey(address, maskLen);
        Map<String, ?> daoMap = dao.get(mappingServiceKey);
        MappingServiceValue value = null;
        if (daoMap != null) {
            value = (MappingServiceValue) daoMap.get("value");
            if (value == null) {
                value = new MappingServiceValue();
            }
        } else {
            value = new MappingServiceValue();
        }
        value.setKey(key);
        MappingEntry<MappingServiceValue> entry = new MappingEntry<MappingServiceValue>("value", value);
        dao.put(mappingServiceKey, entry);
        return true;
    }

    public boolean shouldAuthenticate() {
        return shouldAuthenticate;
    }

    public boolean shouldIterateMask() {
        return shouldIterateMask;
    }

    public void setShouldIterateMask(boolean shouldIterateMask) {
        this.shouldIterateMask = shouldIterateMask;
    }

    public void setShouldAuthenticate(boolean shouldAuthenticate) {
        this.shouldAuthenticate = shouldAuthenticate;
    }

}
