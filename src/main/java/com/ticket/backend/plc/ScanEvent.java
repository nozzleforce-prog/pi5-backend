package com.ticket.backend.plc;

public record ScanEvent(String scannerId, String barcode, int operation) {}
