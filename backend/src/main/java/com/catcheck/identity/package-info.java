@org.springframework.modulith.ApplicationModule(allowedDependencies = {
        "shared",
        "audit::api",
        "media::api",
        "notification::api",
        "privacy::spi" })
package com.catcheck.identity;
