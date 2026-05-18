package com.pfe.MainPackage.Entity;

public class ZmProfile {
    private String zoneName;   // ex: "Grand Tunis"
    private String zoneCode;   // ex: "ZT-01"
    private String coverage;   // free text

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }

    public String getCoverage() { return coverage; }
    public void setCoverage(String coverage) { this.coverage = coverage; }
}
