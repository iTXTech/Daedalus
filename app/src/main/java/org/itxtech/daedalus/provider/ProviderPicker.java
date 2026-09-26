package org.itxtech.daedalus.provider;

import org.itxtech.daedalus.Daedalus;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public abstract class ProviderPicker {
    public static final int DNS_QUERY_METHOD_UDP = 0;
    public static final int DNS_QUERY_METHOD_TCP = 1;
    public static final int DNS_QUERY_METHOD_TLS = 2;
    public static final int DNS_QUERY_METHOD_HTTPS_IETF = 3;
    public static final int DNS_QUERY_METHOD_HTTPS_JSON = 4;
    //This section mush be the same as the one in arrays.xml

    /**
     * Transport for servers on port 53: UDP or TCP. DoT and DoH are chosen per server by
     * {@link DnsTransport}, so the TLS/HTTPS values of older versions fall back to UDP.
     */
    public static int getDnsQueryMethod() {
        try {
            int method = Integer.parseInt(Daedalus.getPrefs().getString("settings_dns_query_method", "0"));
            return method == DNS_QUERY_METHOD_TCP ? DNS_QUERY_METHOD_TCP : DNS_QUERY_METHOD_UDP;
        } catch (Exception e) {
            return DNS_QUERY_METHOD_UDP;
        }
    }
}
