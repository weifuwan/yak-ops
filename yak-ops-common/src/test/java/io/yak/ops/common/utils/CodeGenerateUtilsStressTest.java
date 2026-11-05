package io.yak.ops.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class CodeGenerateUtilsStressTest {

    @Test
    void testNoGenerateDuplicateCode() {
        int codeNum = 1000000;
        List<Long> existsCode = new ArrayList<>();
        for (int i = 0; i < codeNum; i++) {
            Long currentCode = CodeGenerateUtils.genCode();
            existsCode.add(currentCode);
        }
        Set<Long> existsCodeSet = new HashSet<>(existsCode);
        // Disallow duplicate code
        assertEquals(existsCode.size(), existsCodeSet.size());
    }

    @Test
    void testNoGenerateDuplicateCodeWithDifferentAppName() throws UnknownHostException, InterruptedException {
        int threadNum = 10;
        int codeNum = 1000000;

        final String hostName = InetAddress.getLocalHost().getHostName();
        Map<String, List<Long>> machineCodes = new ConcurrentHashMap<>();
        CountDownLatch countDownLatch = new CountDownLatch(threadNum);

        for (int i = 0; i < threadNum; i++) {
            final int c = i;
            new Thread(() -> {
                List<Long> codes = new ArrayList<>(codeNum);
                CodeGenerateUtils.CodeGenerator codeGenerator = new CodeGenerateUtils.CodeGenerator(hostName + "-" + c);
                for (int j = 0; j < codeNum; j++) {
                    codes.add(codeGenerator.genCode());
                }
                machineCodes.put(Thread.currentThread().getName(), codes);
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
        Set<Long> totalCodes = new HashSet<>();
        machineCodes.values().forEach(totalCodes::addAll);
        assertEquals(codeNum * threadNum, totalCodes.size());
    }
}
