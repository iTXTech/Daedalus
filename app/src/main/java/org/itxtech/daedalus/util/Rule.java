package org.itxtech.daedalus.util;

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
public class Rule {
    public static final int TYPE_HOSTS = 0;
    public static final int TYPE_DNAMASQ = 1;

    private String name;
    private String fileName;
    private int type;
    private String downloadUrl;
    private boolean using;

    public Rule(String name, String fileName, int type, String downloadUrl) {
        this.name = name;
        this.fileName = fileName;
        this.type = type;
        this.downloadUrl = downloadUrl;
        this.using = false;
    }

    public void setUsing(boolean using) {
        this.using = using;
    }

    public boolean isUsing() {
        return using;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public int getType() {
        return type;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
