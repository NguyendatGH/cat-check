@org.springframework.modulith.ApplicationModule(allowedDependencies = {
        "shared",
        "audit::api",
        "media::api",
        "notification::api",
        "privacy::spi",
        "credit::api" })
package com.catcheck.identity;
