package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.common.ILogService;
import io.yak.ops.extract.common.service.impl.LogServiceImpl;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MasterLogServiceImpl extends LogServiceImpl implements ILogService {

}
