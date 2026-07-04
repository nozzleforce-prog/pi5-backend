package com.ticket.backend.config;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.OperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * site-config.yml icindeki operasyon ve cihaz tanimlarini MongoDB ile esitler.
 */
@Component
public class SiteDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SiteDataBootstrap.class);

    private final SiteConfigurationProperties siteConfig;
    private final OperationRepository operationRepository;
    private final DeviceRepository deviceRepository;

    public SiteDataBootstrap(
            SiteConfigurationProperties siteConfig,
            OperationRepository operationRepository,
            DeviceRepository deviceRepository) {
        this.siteConfig = siteConfig;
        this.operationRepository = operationRepository;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        syncOperations();
        syncDevices();
    }

    private void syncOperations() {
        prepareOperationCodeChanges();
        for (SiteConfigurationProperties.OperationDef def : siteConfig.getOperations()) {
            Operation op = operationRepository.findByNameIgnoreCase(def.getName().trim())
                    .or(() -> operationRepository.findByOperationCode(def.getCode()))
                    .orElseGet(Operation::new);
            op.setOperationCode(def.getCode());
            op.setName(def.getName().trim());
            op.setOperationFee(def.getFee());
            op.setDurationSeconds(def.getDurationSeconds() > 0 ? def.getDurationSeconds() : 30);
            operationRepository.save(op);
            log.info("Operation synced: code={} name={} fee={} durationSec={}",
                    op.getOperationCode(), op.getName(), op.getOperationFee(), op.getDurationSeconds());
        }
    }

    private void prepareOperationCodeChanges() {
        for (SiteConfigurationProperties.OperationDef def : siteConfig.getOperations()) {
            operationRepository.findByNameIgnoreCase(def.getName().trim())
                    .filter(op -> op.getOperationCode() != def.getCode())
                    .ifPresent(op -> {
                        op.setOperationCode(-100000 - def.getCode());
                        operationRepository.save(op);
                    });
        }
    }

    private void syncDevices() {
        for (SiteConfigurationProperties.DeviceDef def : siteConfig.getDevices()) {
            Operation operation = operationRepository.findByOperationCode(def.getOperationCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown operation-code in site-config: " + def.getOperationCode()));

            Device device = deviceRepository.findByDeviceId(def.getDeviceId())
                    .orElseGet(Device::new);
            device.setDeviceId(def.getDeviceId());
            device.setDeviceIp(def.getReaderIp().trim());
            device.setPlcBit(def.getPlcBit());
            device.setOperationId(operation.getId());
            device.setActive(def.isActive());
            if (def.getReaderType() != null && !def.getReaderType().isBlank()) {
                device.setReaderType(def.getReaderType().trim());
            }
            deviceRepository.save(device);
            log.info("Device synced: deviceId={} readerIp={} plcBit={} operationCode={} readerType={}",
                    device.getDeviceId(), device.getDeviceIp(), device.getPlcBit(), def.getOperationCode(),
                    device.getReaderType());
        }
    }
}
