package com.visiumfarm;

public class Theme {

    public static final String BLUE = "btn-blue";
    public static final String GREEN = "btn-green";
    public static final String RED = "btn-red";
    public static final String ORANGE = "btn-orange";
    public static final String DARK_BUTTON = "btn-dark";

    public static String rootStyle() {
        return ""; // Managed by style.css (.root class)
    }

    public static String cardStyle() {
        return ""; // Managed by style.css (.card class)
    }

    public static String buttonStyle(String colorClass) {
        return ""; // Managed by style.css (.button class)
    }

    public static String titleStyle() {
        return ""; // Managed by style.css (.title class)
    }

    public static String smallLabelStyle() {
        return ""; // Managed by style.css (.label-small class)
    }

    public static String terminalStyle() {
        return ""; // Managed by style.css (.text-area class)
    }
}