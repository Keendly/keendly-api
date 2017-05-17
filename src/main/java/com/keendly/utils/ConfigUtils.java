package com.keendly.utils;

public class ConfigUtils {

    public static String parameter(String key){
        return System.getenv(key);
    }
}
