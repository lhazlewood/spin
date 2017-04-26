package com.leshazlewood.spin.plugin

class Plugins {

    static String configPath(Object... params) {
        if (!params) {
            return ""
        }

        StringBuilder sb = new StringBuilder()
        for(Object param : params) {
            String sval = String.valueOf(param)
            if (sval.contains(".")) {
                sval = "'$sval'" as String
            }
            if (sb.size() > 0) {
                sb.append(".")
            }
            sb.append(sval)
        }

        return sb.toString()
    }
}
