package com.ticket.backend;

import com.ticket.backend.dto.request.AddDeviceRequest;
import com.ticket.backend.dto.request.AddOperationRequest;
import com.ticket.backend.dto.request.EditDeviceRequest;
import com.ticket.backend.dto.request.EditOperationRequest;
import com.ticket.backend.dto.response.DeviceResponse;
import com.ticket.backend.dto.response.OperationResponse;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.OperationRepository;
import com.ticket.backend.rfid.RfidDeviceNetworkScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kullanici akisini simule eder (REST API uzerinden).
 * Calistirma: .\scripts\run-device-simulation-test.ps1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeviceOperationUserSimulationTest {

    private static final Random RND = new Random(42);
    private static final List<String> MOCK_SCAN_IPS =
            List.of("169.254.179.2", "192.168.1.100", "192.168.2.100");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private OperationRepository operationRepository;

    @MockBean
    private RfidDeviceNetworkScanner networkScanner;

    private String base() {
        return "http://localhost:" + port + "/api/devices";
    }

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        operationRepository.deleteAll();
        when(networkScanner.scanConnectedDeviceIps()).thenReturn(MOCK_SCAN_IPS);
    }

    @Test
    void simulateFullUserWorkflow() {
        logStep(1, "3 operasyon ekle (rastgele ucret)");
        OperationResponse water = postOperation("WATER", randomFee());
        OperationResponse foam = postOperation("FOAM", randomFee());
        OperationResponse vacuum = postOperation("VACUUM", randomFee());
        log("  WATER fee=%d, FOAM fee=%d, VACUUM fee=%d".formatted(
                water.getOperationFee(), foam.getOperationFee(), vacuum.getOperationFee()));

        assertThat(postDuplicateOperation("WATER").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        logStep(2, "Bagli IP taramasi");
        ResponseEntity<Map<String, List<String>>> scan = rest.exchange(
                base() + "/scan", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertThat(scan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scan.getBody().get("ips")).containsExactlyElementsOf(MOCK_SCAN_IPS);
        verify(networkScanner, atLeastOnce()).scanConnectedDeviceIps();
        log("  IPs: " + scan.getBody().get("ips"));

        when(networkScanner.scanConnectedDeviceIps()).thenReturn(List.of());
        assertThat(rest.exchange(base() + "/scan", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, List<String>>>() {})
                .getBody().get("ips")).isEmpty();
        when(networkScanner.scanConnectedDeviceIps()).thenReturn(MOCK_SCAN_IPS);
        log("  Bos tarama OK");

        logStep(3, "2 cihaz olustur");
        List<String> ips = scan.getBody().get("ips");
        DeviceResponse d1 = postDevice(ips.get(0), "WATER");
        DeviceResponse d2 = postDevice(ips.get(1), "FOAM");
        assertThat(d1.getDeviceId()).isEqualTo("1");
        assertThat(d2.getDeviceId()).isEqualTo("2");
        assertThat(d1.getPlcBit()).isEqualTo(1);
        assertThat(d2.getPlcBit()).isEqualTo(2);
        log("  Device1 %s -> WATER, Device2 %s -> FOAM".formatted(d1.getDeviceIp(), d2.getDeviceIp()));

        AddDeviceRequest dupIp = new AddDeviceRequest();
        dupIp.setDeviceIp(ips.get(0));
        dupIp.setOperationName("VACUUM");
        assertThat(rest.postForEntity(base(), dupIp, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        logStep(4, "Listele");
        assertThat(rest.getForEntity(base() + "/operations", OperationResponse[].class).getBody()).hasSize(3);
        assertThat(rest.getForEntity(base(), DeviceResponse[].class).getBody()).hasSize(2);
        log("  3 operations, 2 devices");

        logStep(5, "Duzenle");
        EditOperationRequest opEdit = new EditOperationRequest();
        opEdit.setOperationFee(77);
        rest.exchange(base() + "/operations/" + water.getId(), HttpMethod.PUT,
                new HttpEntity<>(opEdit), OperationResponse.class);
        EditDeviceRequest devEdit = new EditDeviceRequest();
        devEdit.setOperationName("VACUUM");
        rest.exchange(base() + "/" + d1.getDeviceId(), HttpMethod.PUT,
                new HttpEntity<>(devEdit), DeviceResponse.class);
        log("  WATER fee=77, Device1 operation=VACUUM");

        logStep(6, "Duzenleme sonrasi listele");
        assertThat(rest.getForEntity(base() + "/operations/" + water.getId(),
                OperationResponse.class).getBody().getOperationFee()).isEqualTo(77);
        assertThat(rest.getForEntity(base() + "/" + d1.getDeviceId(),
                DeviceResponse.class).getBody().getOperation().getName()).isEqualTo("VACUUM");

        logStep(7, "Kullanımdaki operasyon silinemez (FOAM device2'de)");
        ResponseEntity<Map> failDelete = rest.exchange(
                base() + "/operations/" + foam.getId(),
                HttpMethod.DELETE, null, Map.class);
        assertThat(failDelete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        log("  Delete FOAM -> 400: " + failDelete.getBody().get("error"));

        logStep(8, "Kullanılmayan operasyon sil (WATER — device1 artik VACUUM)");
        ResponseEntity<Void> okDelete = rest.exchange(
                base() + "/operations/" + water.getId(),
                HttpMethod.DELETE, null, Void.class);
        assertThat(okDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        log("  Delete WATER -> 204");

        logStep(9, "Son liste");
        List<OperationResponse> opsLeft = Arrays.asList(
                rest.getForEntity(base() + "/operations", OperationResponse[].class).getBody());
        assertThat(opsLeft).hasSize(2);
        assertThat(opsLeft).extracting(OperationResponse::getName)
                .containsExactlyInAnyOrder("FOAM", "VACUUM");
        assertThat(rest.getForEntity(base(), DeviceResponse[].class).getBody()).hasSize(2);
        log("  Operations: " + opsLeft.stream().map(OperationResponse::getName).toList());

        ResponseEntity<DeviceResponse> inact = rest.postForEntity(
                base() + "/" + d2.getDeviceId() + "/inactivate", null, DeviceResponse.class);
        assertThat(inact.getBody().isActive()).isFalse();
        log("  Device2 inactivated");

        System.out.println("\n========== TUM ADIMLAR BASARILI ==========\n");
    }

    private OperationResponse postOperation(String name, int fee) {
        AddOperationRequest req = new AddOperationRequest();
        req.setName(name);
        req.setOperationFee(fee);
        ResponseEntity<OperationResponse> res =
                rest.postForEntity(base() + "/operations", req, OperationResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private ResponseEntity<Map> postDuplicateOperation(String name) {
        AddOperationRequest req = new AddOperationRequest();
        req.setName(name);
        req.setOperationFee(1);
        return rest.postForEntity(base() + "/operations", req, Map.class);
    }

    private DeviceResponse postDevice(String ip, String operationName) {
        AddDeviceRequest req = new AddDeviceRequest();
        req.setDeviceIp(ip);
        req.setOperationName(operationName);
        ResponseEntity<DeviceResponse> res = rest.postForEntity(base(), req, DeviceResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private static int randomFee() {
        return 10 + RND.nextInt(490);
    }

    private static void logStep(int n, String title) {
        System.out.printf("%n========== Step %d: %s ==========%n", n, title);
    }

    private static void log(String msg) {
        System.out.println("  " + msg);
    }
}
