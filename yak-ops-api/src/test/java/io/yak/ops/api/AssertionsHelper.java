package io.yak.ops.api;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;

import java.text.MessageFormat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

public class AssertionsHelper extends Assertions {

    public static void assertThrowServiceException(String message, Executable executable) {
        ServiceException exception = Assertions.assertThrows(ServiceException.class, executable);
        Assertions.assertEquals(message, exception.getMessage());
    }

    public static void assertThrowsServiceException(Status status, Executable executable) {
        ServiceException exception = Assertions.assertThrows(ServiceException.class, executable);
        Assertions.assertEquals(status.getCode(), exception.getCode());
    }

    public static void assertThrowsServiceException(String message, Executable executable) {
        ServiceException exception = Assertions.assertThrows(ServiceException.class, executable);
        Assertions.assertEquals(MessageFormat.format(Status.INTERNAL_SERVER_ERROR_ARGS.getMsg(), message),
                exception.getMessage());
    }

    public static void assertDoesNotThrow(Executable executable) {
        Assertions.assertDoesNotThrow(executable);
    }

}
