package io.yak.ops.plugin.alert.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StreamGobbler extends Thread {

    private final InputStream inputStream;

    StreamGobbler(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void run() {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader inputBufferReader = new BufferedReader(inputStreamReader);

        try {
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = inputBufferReader.readLine()) != null) {
                output.append(line);
                output.append(System.getProperty("line.separator"));
            }
            if (output.length() > 0) {
                log.info("out put msg is{}", output);
            }
        } catch (IOException e) {
            log.error("I/O error occurs {}", e.getMessage());
        } finally {
            try {
                inputBufferReader.close();
                inputStreamReader.close();
            } catch (IOException e) {
                log.error("I/O error occurs {}", e.getMessage());
            }
        }
    }

}
