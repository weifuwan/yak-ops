package io.yak.ops.plugin.task.api.shell;

import java.io.IOException;

/**
 * This interface is used to execute shell commands.
 * It should be created by @{@link IShellInterceptorBuilder}.
 */
public interface IShellInterceptor {

    Process execute() throws IOException;

}
