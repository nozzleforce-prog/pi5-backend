package com.ticket.backend.rfid;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * RFID okuma: veritabanindaki bilet + cihaz eslesmesini kontrol eder ve terminale yazar.
 */
@Service
public class RfidCardLookupService {

    private static final Logger log = LoggerFactory.getLogger(RfidCardLookupService.class);

    private final DeviceRepository deviceRepository;
    private final TicketRepository ticketRepository;

    @Value("${rfid.console.print:true}")
    private boolean consolePrint;

    public RfidCardLookupService(DeviceRepository deviceRepository, TicketRepository ticketRepository) {
        this.deviceRepository = deviceRepository;
        this.ticketRepository = ticketRepository;
    }

    public LookupResult lookup(RfidScanEvent event) {
        String cardId = event.cardId();
        String deviceId = event.deviceId();

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        Optional<Ticket> ticketOpt = ticketRepository.findByBarcode(cardId);

        LookupResult result = new LookupResult(
                cardId,
                deviceId,
                deviceOpt.map(Device::getDeviceIp).orElse(null),
                deviceOpt.isPresent(),
                deviceOpt.map(Device::getPlcBit).orElse(-1),
                ticketOpt.isPresent(),
                ticketOpt.map(Ticket::getName).orElse(null),
                ticketOpt.map(Ticket::getBalance).orElse(null),
                ticketOpt.map(t -> t.getStatus() != null ? t.getStatus().name() : null).orElse(null));

        log.info("[RFID-LOOKUP] cardId={} deviceId={} deviceKnown={} plcBit={} ticketKnown={}",
                cardId, deviceId, deviceOpt.isPresent(),
                deviceOpt.map(Device::getPlcBit).orElse(-1), ticketOpt.isPresent());

        if (consolePrint) {
            printToTerminal(result);
        }
        return result;
    }

    private void printToTerminal(LookupResult result) {
        System.out.println();
        System.out.println("========== RFID SCAN ==========");
        System.out.printf("  cardId   : %s%n", result.cardId());
        System.out.printf("  deviceId : %s%n", result.deviceId());
        if (result.readerIp() != null) {
            System.out.printf("  readerIp : %s%n", result.readerIp());
        }
        if (result.ticketKnown()) {
            System.out.printf("  ticket   : FOUND  name=%s  balance=%d  status=%s%n",
                    result.ticketName(), result.ticketBalance(), result.ticketStatus());
        } else {
            System.out.println("  ticket   : NOT IN DATABASE");
        }
        if (result.deviceKnown()) {
            System.out.printf("  device   : KNOWN  plcBit=%d (site-config -> mod 40001)%n", result.plcBit());
        } else {
            System.out.println("  device   : NOT IN DATABASE");
        }
        System.out.println("===============================");
        System.out.println();
    }

    public record LookupResult(
            String cardId,
            String deviceId,
            String readerIp,
            boolean deviceKnown,
            int plcBit,
            boolean ticketKnown,
            String ticketName,
            Integer ticketBalance,
            String ticketStatus) {
    }
}
