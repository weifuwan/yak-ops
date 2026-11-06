package io.yak.ops.server.master.engine.workflow.serial;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.model.SerialCommandDto;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.dao.repository.SerialCommandDao;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SerialCommandLaunchTest {

    @Test
    void onlyOneMasterCanLaunchTheSameWaitingCommand() {
        SerialCommandWaitHandler handler = new SerialCommandWaitHandler();
        handler.serialCommandDao = mock(SerialCommandDao.class);
        handler.commandDao = mock(CommandDao.class);
        handler.transactionTemplate = mock(TransactionTemplate.class);
        when(handler.transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(handler.serialCommandDao.tryLaunch(12)).thenReturn(true, false);
        Command command = new Command();
        SerialCommandDto pending = SerialCommandDto.builder().id(12)
                .state(SerialCommandDto.State.WAITING).command(command).build();

        handler.launchSerialCommand(pending);
        handler.launchSerialCommand(pending);

        verify(handler.serialCommandDao, times(2)).tryLaunch(12);
        verify(handler.commandDao).insert(command);
    }
}
