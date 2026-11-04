package io.yak.ops.api.exceptions;

import io.yak.ops.api.enums.Status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ServiceExceptionTest {

    @Test
    public void getCodeTest() {
        ServiceException serviceException = new ServiceException();
        Assertions.assertEquals(Status.INTERNAL_SERVER_ERROR_ARGS.getCode(), serviceException.getCode());

        serviceException = new ServiceException(Status.ALERT_GROUP_EXIST);
        Assertions.assertEquals(Status.ALERT_GROUP_EXIST.getCode(), serviceException.getCode());

        serviceException = new ServiceException(10012, "alarm group already exists");
        Assertions.assertEquals(10012, serviceException.getCode());
    }
    @Test
    public void getMessageTest() {
        ServiceException serviceException = new ServiceException();
        Assertions.assertEquals(Status.INTERNAL_SERVER_ERROR_ARGS.getMsg(), serviceException.getMessage());

        serviceException = new ServiceException(Status.ALERT_GROUP_EXIST);
        Assertions.assertNotNull(serviceException.getMessage());

        serviceException = new ServiceException(10012, "alarm group already exists");
        Assertions.assertNotNull(serviceException.getMessage());
    }
}
