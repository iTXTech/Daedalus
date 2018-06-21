package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;

/**
 * @author PeratX
 */
public class ProviderPicker {
    public static final int DNS_QUERY_METHOD_UDP = 0;
    public static final int DNS_QUERY_METHOD_TCP = 1;
    public static final int DNS_QUERY_METHOD_HTTPS = 2;

    public static Provider getProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        switch (Daedalus.getPrefs().getInt("settings_dns_query_method", DNS_QUERY_METHOD_UDP)) {
            case DNS_QUERY_METHOD_UDP:
                return new UdpProvider(descriptor, service);
            case DNS_QUERY_METHOD_TCP:
                return new TcpProvider(descriptor, service);
            case DNS_QUERY_METHOD_HTTPS:
                //TODO
                break;
        }
        return new UdpProvider(descriptor, service);
    }
}
