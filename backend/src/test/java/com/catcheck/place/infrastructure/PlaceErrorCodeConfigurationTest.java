package com.catcheck.place.infrastructure;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ErrorCodeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceErrorCodeConfigurationTest {

    @Test
    void everyPlaceErrorCodeIsRegisteredExactlyOnce() throws Exception {
        PlaceErrorCodeConfiguration configuration = new PlaceErrorCodeConfiguration();
        List<ErrorCode> registered = new ArrayList<>();
        for (Method method : PlaceErrorCodeConfiguration.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                method.setAccessible(true);
                registered.add((ErrorCode) method.invoke(configuration));
            }
        }

        assertThat(registered).containsExactlyInAnyOrder(PlaceErrorCode.values());
        new ErrorCodeRegistry(registered);
    }
}
