package org.itxtech.daedalus;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
class DnsServers {
    static String getDnsServerAddress(String id) {
        switch (id) {
            case "0":
                return "113.107.249.56";
            case "1":
                return "120.27.103.230";
            case "2":
                return "123.206.61.167";
            default:
                return "123.206.61.167";
        }
    }
}
