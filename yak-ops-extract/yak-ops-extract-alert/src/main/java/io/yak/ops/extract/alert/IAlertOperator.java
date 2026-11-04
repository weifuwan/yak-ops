package io.yak.ops.extract.alert;

import io.yak.ops.extract.alert.request.AlertSendRequest;
import io.yak.ops.extract.alert.request.AlertSendResponse;
import io.yak.ops.extract.alert.request.AlertTestSendRequest;
import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;

@RpcService
public interface IAlertOperator {

    @RpcMethod
    AlertSendResponse sendAlert(AlertSendRequest alertSendRequest);

    @RpcMethod
    AlertSendResponse sendTestAlert(AlertTestSendRequest alertSendRequest);

}
