package org.itxtech.daedalus.serverprovider;

import io.netty.channel.Channel;

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

    public static Provider getProvider(Channel channel) {
        switch (org.itxtech.daedalus.provider.ProviderPicker.getDnsQueryMethod()) {
            case DNS_QUERY_METHOD_UDP:
                return new UdpProvider(channel);
        }
        return new UdpProvider(channel);
    }
}
