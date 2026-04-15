package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class StompSenderService {

    public void sendToQueue(String queueName, String jsonPayload) {
        try (Socket socket = new Socket("localhost", 61613)) {
            OutputStream out = socket.getOutputStream();

            // Send CONNECT frame
            String connectFrame = "CONNECT\naccept-version:1.1,1.2\nhost:localhost\nlogin:admin\npasscode:admin\n\n\0";
            out.write(connectFrame.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Wait for CONNECTED (Simplification: assuming immediate response)
            Thread.sleep(100); 

            // Send SEND frame
            String sendFrame = "SEND\ndestination:jms.queue." + queueName + "\ncontent-type:application/json\n\n" + jsonPayload + "\0";
            out.write(sendFrame.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Send DISCONNECT frame
            String disconnectFrame = "DISCONNECT\nreceipt:77\n\n\0";
            out.write(disconnectFrame.getBytes(StandardCharsets.UTF_8));
            out.flush();

            log.info("Successfully sent STOMP message to {}", queueName);
        } catch (Exception e) {
            log.error("Failed to send STOMP message to {}", queueName, e);
            throw new RuntimeException("STOMP transmission failed", e);
        }
    }
}
