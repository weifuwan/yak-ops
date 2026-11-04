package org.apache.flink.cdc.connectors.base.utils;

/** Used to compare version numbers at runtime. */
public class VersionComparable implements Comparable<VersionComparable> {

    private int majorVersion;
    private int minorVersion;
    private int patchVersion;
    private String versionString;

    private VersionComparable(String versionString) {
        this.versionString = versionString;
        try {
            int pos = versionString.indexOf('-');
            String numberPart = versionString;
            if (pos > 0) {
                numberPart = versionString.substring(0, pos);
            }

            String[] versions = numberPart.split("\\.");

            this.majorVersion = Integer.parseInt(versions[0]);
            this.minorVersion = Integer.parseInt(versions[1]);
            if (versions.length == 3) {
                this.patchVersion = Integer.parseInt(versions[2]);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Can not recognize version %s.", versionString));
        }
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getPatchVersion() {
        return patchVersion;
    }

    public static VersionComparable fromVersionString(String versionString) {
        return new VersionComparable(versionString);
    }

    @Override
    public int compareTo(VersionComparable version) {
        if (equalTo(version)) {
            return 0;
        } else if (newerThan(version)) {
            return 1;
        } else {
            return -1;
        }
    }

    public boolean equalTo(VersionComparable version) {
        return majorVersion == version.majorVersion
                && minorVersion == version.minorVersion
                && patchVersion == version.patchVersion;
    }

    public boolean newerThan(VersionComparable version) {
        if (majorVersion <= version.majorVersion) {
            if (majorVersion < version.majorVersion) {
                return false;
            } else {
                if (minorVersion <= version.minorVersion) {
                    if (minorVersion < version.patchVersion) {
                        return false;
                    } else {
                        return patchVersion > version.patchVersion;
                    }
                }
            }
        }
        return true;
    }

    public boolean newerThanOrEqualTo(VersionComparable version) {
        return newerThan(version) || equalTo(version);
    }

    @Override
    public String toString() {
        return versionString;
    }
}
