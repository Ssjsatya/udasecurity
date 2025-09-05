
module com.udacity.catpoint.security {
    requires java.desktop;
    requires com.google.gson;
    requires com.google.common;

    exports com.udacity.catpoint.security.application;
    exports com.udacity.catpoint.security.data;
    exports com.udacity.catpoint.security.service;
}