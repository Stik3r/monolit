package org.monolites.monolit.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
@Service
public class AvailabilityCheckService {

    public boolean pingHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);

            return address.isReachable(5000);
        }
        catch (Exception e) {
            log.error("Ошибка при проверке хоста {}", e.getMessage());
            return false;
        }
    }

    public boolean pingService(String host, String port) {
        try (Socket socket = new Socket()){
            socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 5000);
            return true;
        }
        catch (Exception e) {
            log.error("Ошибка работы или сервис недоступен {}", e.getMessage());
            return false;
        }
    }

    public String checkAvailability(String host) {
        return pingHost(host) ? null : "Сервер недоступен";
    }
}
