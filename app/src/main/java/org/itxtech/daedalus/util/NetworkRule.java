package org.itxtech.daedalus.util;

import java.util.ArrayList;
import java.util.List;

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

/**
 * A rule of Settings > Network Rules: which primary/secondary servers to use while the
 * device is on a certain network. Subnet rules are checked first (in list order), then
 * mobile data rules; when nothing matches, the primary/secondary servers of the settings
 * are used.
 */
public class NetworkRule {
    public static final int TYPE_SUBNET = 0;
    public static final int TYPE_MOBILE = 1;

    private int type = TYPE_SUBNET;
    // Comma separated CIDRs, e.g. "192.168.31.0/24, 10.0.0.0/8"; only for TYPE_SUBNET
    private String subnets = "";
    // Server IDs as used by the "primary_server" / "secondary_server" preferences
    private String primary;
    private String secondary;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getSubnets() {
        return subnets == null ? "" : subnets;
    }

    public void setSubnets(String subnets) {
        this.subnets = subnets;
    }

    /**
     * The CIDRs of a subnet rule, split on commas, semicolons, spaces and line breaks.
     */
    public List<String> getSubnetList() {
        ArrayList<String> list = new ArrayList<>();
        for (String part : getSubnets().split("[,;\\s]+")) {
            if (!part.isEmpty()) {
                list.add(part);
            }
        }
        return list;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public String getSecondary() {
        return secondary;
    }

    public void setSecondary(String secondary) {
        this.secondary = secondary;
    }
}
