package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.OutgoingEmail;

public interface EmailTransport {

    void deliver(OutgoingEmail email);
}
